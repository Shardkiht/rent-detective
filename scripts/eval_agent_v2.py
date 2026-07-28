#!/usr/bin/env python3
"""
Agent V2 + RAG 增强版评测脚本
核心改进：
1. 增加语义分析工具（检测中介套话、引流、身份矛盾等）
2. 改进工具参数设计（允许无价格时的分析）
3. 增加 embedding 缓存
4. 优化 System Prompt
"""

import csv
import json
import os
import re
import time
import requests
import sys
import numpy as np
from collections import defaultdict
from pathlib import Path

# ==================== 配置 ====================
API_BASE = "https://api.siliconflow.cn/v1"
API_KEY = os.environ.get("SILICONFLOW_API_KEY", "")
if not API_KEY:
    print("⚠️  警告：环境变量 SILICONFLOW_API_KEY 未设置", file=sys.stderr)
LLM_MODEL = "deepseek-ai/DeepSeek-V3.1-Terminus"
EMBEDDING_MODEL = "BAAI/bge-large-zh-v1.5"

RESOURCE_DIR = Path(__file__).parent.parent / "src" / "main" / "resources"
CASE_LIB_CSV = RESOURCE_DIR / "案例库_80条.csv"
EVAL_CSV = RESOURCE_DIR / "评测集_24条.csv"
VECTOR_CACHE = RESOURCE_DIR / "case_library_vectors.json"

MAX_STEPS = 6  # 减少步数，提高效率

VALID_VERDICTS = {"SAFE", "SUSPICIOUS", "REVIEW", "INSUFFICIENT", "NOT_LISTING"}

# Embedding 缓存
_embedding_cache = {}

# ==================== 语义分析规则（简化版规则引擎）====================
SEMANTIC_RULES = {
    "agent_stock_phrase": {
        "name": "中介套话",
        "patterns": [
            "无中介费", "无任何费用", "零中介费", "0中介费",
            "押一付一", "押一付三", "押二付一",
            "看房随时", "随时看房", "看房联系",
            "免费咨询", "专业推荐", "更多房源",
            "房源多多", "房源丰富", "多种户型",
            "全杭州", "全市", "多个区域"
        ],
        "weight": 0.7  # 多条命中才触发
    },
    "wechat_drainage": {
        "name": "引流微信号",
        "patterns": [
            "加微信", "加V", "加v", "VX", "vx",
            "微信同步", "微电同步", "电话同微信",
            "看视频", "视频看房", "视频实拍",
            "微信号:", "微信：", "联系方式:", "联系电话:",
            "私信", "私聊", "加我",
            "wx", "WX"
        ],
        "weight": 1.0  # 高权重
    },
    "phone_obfuscation": {
        "name": "电话混淆",
        "patterns": [
            "1[3-9]\\d{9}",  # 手机号
            "微信", "微", "V",
            "私信", "私聊"
        ],
        "weight": 0.5
    },
    "identity_mixed": {
        "name": "身份矛盾",
        "patterns": [
            "个人房东.*中介", "中介.*个人房东",
            "房东直租.*转租", "转租.*房东直租",
            "个人房源.*代理", "代理.*个人房源"
        ],
        "weight": 1.0
    },
    "emotional_narrative": {
        "name": "情感叙事",
        "patterns": [
            "我爱", "喜欢", "非常", "真的",
            "宝贝", "亲亲", "姐妹",
            "哭", "难过", "最后",
            "急", "快点", "赶紧"
        ],
        "weight": 0.3
    },
    "coverage_language": {
        "name": "覆盖性语言",
        "patterns": [
            "各种", "各类", "多种", "不同",
            "适合", "满足", "适合各种",
            "周边", "附近", "周围"
        ],
        "weight": 0.5
    }
}

def analyze_text_patterns(text):
    """分析文本中的风险模式。"""
    results = []
    for rule_id, rule in SEMANTIC_RULES.items():
        matches = []
        for pattern in rule["patterns"]:
            if re.search(pattern, text):
                matches.append(pattern)
        if matches:
            # 根据命中数量和权重计算得分
            score = len(matches) * rule["weight"] / 5.0  # 归一化
            if len(matches) >= 2:  # 至少2个匹配才算有效
                results.append({
                    "rule_id": rule_id,
                    "rule_name": rule["name"],
                    "matches": matches[:3],  # 最多显示3个
                    "score": min(score, 1.0),
                    "triggered": True
                })
            elif rule["weight"] >= 0.8 and len(matches) >= 1:  # 高权重规则1个就触发
                results.append({
                    "rule_id": rule_id,
                    "rule_name": rule["name"],
                    "matches": matches[:3],
                    "score": rule["weight"],
                    "triggered": True
                })
    return results

def check_info_completeness(title, description, price, location):
    """检查信息完整性。"""
    missing = []
    if not price or str(price).strip() in ("", "未知", "None", "0"):
        missing.append("价格")
    if not location or str(location).strip() in ("", "未知", "杭州"):
        missing.append("位置")
    if not description or len(str(description).strip()) < 30:
        missing.append("详细描述")
    
    total_score = 1.0 - len(missing) * 0.3
    return {
        "has_price": bool(price and str(price).strip() not in ("", "未知", "None", "0")),
        "has_location": bool(location and str(location).strip() not in ("", "未知", "杭州")),
        "has_description": bool(description and len(str(description).strip()) >= 30),
        "missing_fields": missing,
        "completeness_score": max(0.0, total_score),
        "is_info_insufficient": len(missing) >= 2  # 缺2项以上算信息不足
    }

def check_listing_type(title, description):
    """检查房源类型。"""
    text = f"{title} {description}"
    not_listing_patterns = [
        "找室友", "求租", "合租", "求合租",
        "寻合租", "找合租", "想找",
        "诚寻", "诚心求", "求一个",
        "出租.*求", "求.*出租"
    ]
    
    for pattern in not_listing_patterns:
        if re.search(pattern, text):
            return {
                "is_listing": False,
                "type": "NOT_LISTING",
                "reason": f"匹配非房源模式: {pattern}"
            }
    
    return {
        "is_listing": True,
        "type": "LISTING",
        "reason": "内容符合房源信息特征"
    }

# ==================== Embedding ====================
def get_embedding(text):
    """调用 embedding API，带缓存。"""
    cache_key = text[:100]  # 用前100字符做key（近似）
    if cache_key in _embedding_cache:
        return _embedding_cache[cache_key]
    
    payload = {
        "model": EMBEDDING_MODEL,
        "input": text[:500]  # 限制长度
    }
    headers = {
        "Authorization": f"Bearer {API_KEY}",
        "Content-Type": "application/json"
    }
    for attempt in range(3):
        try:
            resp = requests.post(f"{API_BASE}/embeddings", json=payload, headers=headers, timeout=60)
            resp.raise_for_status()
            data = resp.json()
            vec = data["data"][0]["embedding"]
            _embedding_cache[cache_key] = vec
            return vec
        except Exception as e:
            if attempt < 2:
                time.sleep(2 ** attempt)
            else:
                raise RuntimeError(f"Embedding 失败: {e}")

def cosine_similarity(a, b):
    """计算余弦相似度。"""
    a = np.array(a)
    b = np.array(b)
    return float(np.dot(a, b) / (np.linalg.norm(a) * np.linalg.norm(b)))

def load_or_build_vectors():
    """加载或构建案例库向量。"""
    if VECTOR_CACHE.exists():
        print(f"从缓存加载向量: {VECTOR_CACHE}")
        with open(VECTOR_CACHE, "r") as f:
            data = json.load(f)
        return data["vectors"], data["meta"]
    
    print("构建案例库向量（首次运行，约 80 次 embedding 调用）...")
    case_rows = []
    with open(CASE_LIB_CSV, "r", encoding="utf-8-sig") as f:
        case_rows = list(csv.DictReader(f))
    
    vectors = {}
    meta = {}
    
    for i, row in enumerate(case_rows):
        row_id = int(row["id"])
        title = row.get("title", "")
        description = row.get("description", "")
        text = f"{title}\n{description}"
        
        try:
            vec = get_embedding(text)
            vectors[str(row_id)] = vec
            meta[str(row_id)] = {
                "id": row_id,
                "title": title,
                "price": row.get("price", ""),
                "location": row.get("location", ""),
                "risk_level": row.get("risk_level", ""),
                "risk_tags": row.get("risk_tags", ""),
                "phone": row.get("phone", "")
            }
            if (i + 1) % 10 == 0:
                print(f"  [{i+1}/{len(case_rows)}] 已嵌入")
                time.sleep(0.3)
        except Exception as e:
            print(f"  [跳过] ID={row_id}: {e}")
        time.sleep(0.2)
    
    data = {"vectors": vectors, "meta": meta}
    with open(VECTOR_CACHE, "w") as f:
        json.dump(data, f)
    print(f"向量已缓存: {VECTOR_CACHE}")
    
    return vectors, meta

# ==================== RAG 检索 ====================
def search_similar(query_text, vectors, meta, top_k=5, exclude_ids=None):
    """向量检索 Top-K 相似案例。"""
    if exclude_ids is None:
        exclude_ids = set()
    
    query_vec = get_embedding(query_text)
    
    scored = []
    for row_id, vec in vectors.items():
        if int(row_id) in exclude_ids:
            continue
        sim = cosine_similarity(query_vec, vec)
        scored.append((int(row_id), sim))
    
    scored.sort(key=lambda x: x[1], reverse=True)
    top = scored[:top_k]
    
    results = []
    for row_id, score in top:
        m = meta.get(str(row_id), {})
        results.append({
            "listing_id": row_id,
            "title": m.get("title", "未知"),
            "risk_level": m.get("risk_level", ""),
            "risk_tags": m.get("risk_tags", ""),
            "score": round(score, 4)
        })
    return results

# ==================== 增强版工具 ====================
def tool_search_similar(args, vectors, meta, exclude_ids):
    """search_similar_listings 工具。"""
    description = args.get("description") or args.get("text", "")
    if not description:
        return {"success": False, "error": "缺少参数: description"}
    
    cases = search_similar(description, vectors, meta, top_k=5, exclude_ids=exclude_ids)
    return {"success": True, "data": json.dumps(cases, ensure_ascii=False)}

def tool_analyze_listing(args, vectors, meta, exclude_ids):
    """analyze_listing_content 工具 - 综合分析房源内容。"""
    title = args.get("title", "")
    description = args.get("description", "")
    price = args.get("price")
    location = args.get("location", "")
    
    full_text = f"{title} {description}"
    
    # 1. 检查房源类型
    listing_type = check_listing_type(title, description)
    
    # 2. 检查信息完整性
    completeness = check_info_completeness(title, description, price, location)
    
    # 3. 分析风险模式
    patterns = analyze_text_patterns(full_text)
    
    # 4. 综合评分
    risk_score = 0.0
    
    # 信息缺失扣分
    if completeness["is_info_insufficient"]:
        risk_score += 0.5
    elif completeness["completeness_score"] < 0.7:
        risk_score += 0.2
    
    # 风险模式加分
    for p in patterns:
        risk_score += p["score"]
    
    risk_score = min(risk_score, 1.0)
    
    # 生成分析结果
    result = {
        "listing_type": listing_type,
        "completeness": completeness,
        "detected_patterns": patterns,
        "risk_score": round(risk_score, 2),
        "preliminary_verdict": "",
        "reasoning": []
    }
    
    # 初步判断
    reasoning = []
    if not listing_type["is_listing"]:
        result["preliminary_verdict"] = "NOT_LISTING"
        reasoning.append(f"房源类型异常: {listing_type['reason']}")
    elif completeness["is_info_insufficient"]:
        result["preliminary_verdict"] = "INSUFFICIENT"
        reasoning.append(f"关键信息缺失: {', '.join(completeness['missing_fields'])}")
    elif risk_score >= 0.7:
        result["preliminary_verdict"] = "SUSPICIOUS"
        reasoning.append(f"风险分数较高 ({risk_score:.2f})，检测到 {len(patterns)} 个风险模式")
    elif risk_score >= 0.4:
        result["preliminary_verdict"] = "REVIEW"
        reasoning.append(f"存在一定风险迹象 (分数 {risk_score:.2f})，需要进一步调查")
    else:
        result["preliminary_verdict"] = "SAFE"
        reasoning.append(f"信息完整，风险分数低 ({risk_score:.2f})，未检测到明显风险模式")
    
    result["reasoning"] = reasoning
    
    return {"success": True, "data": json.dumps(result, ensure_ascii=False)}

def tool_check_price_anomaly(args, vectors, meta, exclude_ids):
    """check_price_anomaly 工具。"""
    price = args.get("price")
    description = args.get("description", "")
    location = args.get("location", "")
    
    # 处理无价格情况
    if price is None or (isinstance(price, str) and price.strip() in ("", "未知")):
        return {"success": True, "data": json.dumps({
            "verdict": "NO_PRICE",
            "inputPrice": None,
            "message": "房源未标注价格，建议结合其他信息综合判断"
        }, ensure_ascii=False)}
    
    try:
        price = float(price)
    except (ValueError, TypeError):
        return {"success": True, "data": json.dumps({
            "verdict": "INVALID_PRICE",
            "inputPrice": str(price),
            "message": "价格格式无效"
        }, ensure_ascii=False)}
    
    if price <= 0:
        return {"success": True, "data": json.dumps({
            "verdict": "ANOMALY",
            "inputPrice": price,
            "message": "价格 ≤ 0，明显异常"
        }, ensure_ascii=False)}
    
    # 有价格才做相似案例检索
    search_text = description or location
    if not search_text:
        return {"success": True, "data": json.dumps({
            "verdict": "UNKNOWN",
            "inputPrice": price,
            "message": "缺少描述文本，无法进行相似案例检索"
        }, ensure_ascii=False)}
    
    cases = search_similar(search_text, vectors, meta, top_k=10, exclude_ids=exclude_ids)
    
    comparable_prices = []
    for c in cases:
        if c["score"] < 0.6:
            continue
        m = meta.get(str(c["listing_id"]), {})
        case_price_str = m.get("price", "")
        try:
            case_price = float(case_price_str)
            if case_price > 0:
                comparable_prices.append(case_price)
        except (ValueError, TypeError):
            continue
    
    if len(comparable_prices) < 3:
        return {"success": True, "data": json.dumps({
            "verdict": "INSUFFICIENT_DATA",
            "inputPrice": price,
            "comparableCount": len(comparable_prices),
            "message": f"可比样本不足（仅 {len(comparable_prices)} 条），无法判断"
        }, ensure_ascii=False)}
    
    comparable_prices.sort()
    n = len(comparable_prices)
    if n % 2 == 0:
        median = (comparable_prices[n//2 - 1] + comparable_prices[n//2]) / 2.0
    else:
        median = comparable_prices[n//2]
    
    deviation = (price - median) / median
    if abs(deviation) > 0.35:
        verdict = "ANOMALY"
        message = f"价格 {price} 元偏离相似案例中位数 {median:.0f} 元达 {deviation*100:.1f}%"
    else:
        verdict = "NORMAL"
        message = f"价格 {price} 元处于合理区间（偏离 {deviation*100:.1f}%）"
    
    return {"success": True, "data": json.dumps({
        "verdict": verdict,
        "inputPrice": price,
        "comparableCount": len(comparable_prices),
        "medianPrice": round(median, 2),
        "deviation": round(deviation, 4),
        "message": message
    }, ensure_ascii=False)}

# ==================== Agent V2 ====================
SYSTEM_PROMPT_V2 = """你是一个专业的租房风险调查Agent。你可以使用以下工具：

1. analyze_listing_content: 综合分析房源内容，包括：
   - 检查房源类型（是出租房源还是求租/找室友信息）
   - 评估信息完整性（是否缺价格、位置、描述）
   - 检测风险模式（中介套话、引流微信号、身份矛盾等）
   - 给出初步判断和风险评分

2. search_similar_listings: 在案例库中检索相似案例，参考历史判定

3. check_price_anomaly: 检查价格是否异常偏离市场水平

调查流程：
1. 首先调用 analyze_listing_content 获取初步分析结果
2. 根据初步结果决定是否需要进一步调查：
   - 如果 NOT_LISTING 或 INSUFFICIENT，直接给出结论
   - 如果 SUSPICIOUS，调用 search_similar_listings 查看相似案例
   - 如果有价格，可调用 check_price_anomaly
3. 结论必须是 JSON 格式：{"verdict": "SAFE/SUSPICIOUS/REVIEW/INSUFFICIENT/NOT_LISTING", "confidence": 0-1}

重要规则：
- 信息严重缺失（无价格且无位置）→ INSUFFICIENT
- 内容是求租/找室友 → NOT_LISTING  
- 检测到多个风险模式且无合理解释 → SUSPICIOUS
- 信息完整且风险分低 → SAFE
- 有疑点但证据不足 → REVIEW
"""

def run_agent_v2(listing, vectors, meta, exclude_ids):
    """运行增强版 Agent 调查循环。"""
    title = listing.get('title', '')
    description = listing.get('description', '')
    price = listing.get('price', '')
    location = listing.get('location', '')
    phone = listing.get('phone', '')
    
    listing_info = f"""请调查以下房源：
标题: {title}
价格: {price} 元/月
位置: {location}
描述: {description}
联系电话: {phone}
"""
    
    messages = [
        {"role": "system", "content": SYSTEM_PROMPT_V2},
        {"role": "user", "content": listing_info}
    ]
    
    # 定义增强版工具
    tools = [
        {
            "type": "function",
            "function": {
                "name": "analyze_listing_content",
                "description": "综合分析房源内容，检测风险模式、评估信息完整性、给出初步判断",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "title": {"type": "string", "description": "房源标题"},
                        "description": {"type": "string", "description": "房源描述"},
                        "price": {"type": "number", "description": "价格（可以为null）"},
                        "location": {"type": "string", "description": "位置"}
                    },
                    "required": ["title", "description"]
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "search_similar_listings",
                "description": "在案例库中检索相似案例",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "description": {"type": "string", "description": "房源描述文本"}
                    },
                    "required": ["description"]
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "check_price_anomaly",
                "description": "检查价格是否异常",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "price": {"type": "number", "description": "价格"},
                        "description": {"type": "string", "description": "描述"},
                        "location": {"type": "string", "description": "位置"}
                    },
                    "required": ["price"]
                }
            }
        }
    ]
    
    tools_called = set()
    
    for step in range(1, MAX_STEPS + 1):
        response = call_llm_agent_v2(messages, tools)
        
        tool_calls = response.get("tool_calls", [])
        
        if tool_calls:
            for tc in tool_calls:
                fn_name = tc["function"]["name"]
                fn_args = json.loads(tc["function"]["arguments"])
                
                tools_called.add(fn_name)
                
                if fn_name == "analyze_listing_content":
                    fn_args.setdefault("title", title)
                    fn_args.setdefault("description", description)
                    fn_args.setdefault("price", None if price in ("", "未知", "None") else float(price) if price else None)
                    fn_args.setdefault("location", location)
                    result = tool_analyze_listing(fn_args, vectors, meta, exclude_ids)
                elif fn_name == "search_similar_listings":
                    fn_args.setdefault("description", f"{title} {description}")
                    result = tool_search_similar(fn_args, vectors, meta, exclude_ids)
                elif fn_name == "check_price_anomaly":
                    if price and price not in ("", "未知", "None"):
                        try:
                            fn_args["price"] = float(price)
                        except:
                            fn_args["price"] = None
                    else:
                        fn_args["price"] = None
                    fn_args.setdefault("description", description)
                    fn_args.setdefault("location", location)
                    result = tool_check_price_anomaly(fn_args, vectors, meta, exclude_ids)
                else:
                    result = {"success": False, "error": f"未知工具: {fn_name}"}
                
                messages.append({
                    "role": "assistant",
                    "content": response.get("content", ""),
                    "tool_calls": tool_calls
                })
                messages.append({
                    "role": "tool",
                    "name": fn_name,
                    "content": result["data"] if result["success"] else f"调用失败: {result['error']}",
                    "tool_call_id": tc["id"]
                })
        else:
            content = response.get("content", "")
            verdict = try_parse_verdict(content)
            
            if verdict:
                return verdict
            
            messages.append({"role": "assistant", "content": content})
            messages.append({"role": "user", "content": 
                "请将你的判断结论输出为严格的JSON格式：{\"verdict\": \"SAFE/SUSPICIOUS/REVIEW/INSUFFICIENT/NOT_LISTING\", \"confidence\": 0-1}"})
    
    # 强制收敛
    messages.append({"role": "user", "content": 
        "已达到最大步数，请直接输出结论 JSON：{\"verdict\": \"...\", \"confidence\": ...}"})
    
    final_response = call_llm_agent_v2(messages, [])
    content = final_response.get("content", "")
    verdict = try_parse_verdict(content)
    
    if verdict:
        return verdict
    return "SUSPICIOUS"  # 兜底

def call_llm_agent_v2(messages, tools):
    """调用 LLM，支持工具调用。"""
    payload = {
        "model": LLM_MODEL,
        "temperature": 0.2,
        "messages": messages,
        "tools": tools if tools else None
    }
    if not tools:
        del payload["tools"]
    
    headers = {
        "Authorization": f"Bearer {API_KEY}",
        "Content-Type": "application/json"
    }
    
    for attempt in range(3):
        try:
            resp = requests.post(
                f"{API_BASE}/chat/completions",
                json=payload,
                headers=headers,
                timeout=90
            )
            resp.raise_for_status()
            data = resp.json()
            return data["choices"][0]["message"]
        except Exception as e:
            if attempt < 2:
                time.sleep(2 ** attempt)
            else:
                raise RuntimeError(f"LLM 调用失败: {e}")

def try_parse_verdict(content):
    """尝试从文本中解析 verdict。"""
    try:
        json_str = content.strip()
        start = json_str.find('{')
        end = json_str.rfind('}')
        if start >= 0 and end > start:
            json_str = json_str[start:end+1]
        node = json.loads(json_str)
        verdict = node.get("verdict", "").upper()
        if verdict in VALID_VERDICTS:
            return verdict
    except Exception:
        pass
    return None

# ==================== 评测逻辑 ====================
def judge_correct(row, predicted):
    """判断预测是否正确（与 Java JudgeUtils.judgeCorrect 保持一致）。"""
    eval_group = row.get("eval_group", "normal").strip()
    human_label = row.get("risk_level", "").strip().lower()
    
    if predicted is None or predicted in ("ERROR", "UNKNOWN"):
        return False
    
    pred = predicted.upper()
    
    if eval_group == "normal":
        if human_label == "safe":
            return pred == "SAFE"
        elif human_label == "suspicious":
            return pred == "SUSPICIOUS"
        return False
    elif eval_group in ("insufficient", "info_insufficient"):
        return pred in ("INSUFFICIENT", "REVIEW")
    elif eval_group == "not_listing":
        return pred == "NOT_LISTING"
    return False

def main():
    print("=" * 60)
    print("Agent V2 + RAG 增强版评测 —— 评测集_24条.csv")
    print("=" * 60)
    
    vectors, meta = load_or_build_vectors()
    print(f"案例库向量: {len(vectors)} 条")
    
    eval_rows = []
    with open(EVAL_CSV, "r", encoding="utf-8-sig") as f:
        eval_rows = list(csv.DictReader(f))
    
    print(f"评测集: {len(eval_rows)} 条")
    
    eval_ids = set(int(r["id"]) for r in eval_rows)
    print(f"评测集 IDs: {sorted(eval_ids)}")
    
    groups = defaultdict(lambda: {"total": 0, "correct": 0, "review": 0, "mis": []})
    
    for i, row in enumerate(eval_rows):
        row_id = int(row["id"])
        eval_group = row.get("eval_group", "normal").strip()
        
        print(f"\n{'─'*50}")
        print(f"[{i+1}/{len(eval_rows)}] ID={row_id}, group={eval_group}")
        print(f"  标题: {row.get('title', '')[:60]}...")
        
        try:
            predicted = run_agent_v2(row, vectors, meta, eval_ids)
            correct = judge_correct(row, predicted)
            
            g = groups[eval_group]
            g["total"] += 1
            if predicted == "REVIEW":
                g["review"] += 1
            if correct:
                g["correct"] += 1
                print(f"  ✓ 预测={predicted}")
            else:
                ground_truth = row.get("risk_level", "").upper()
                if eval_group in ("insufficient", "info_insufficient"):
                    ground_truth = "INSUFFICIENT"
                elif eval_group == "not_listing":
                    ground_truth = "NOT_LISTING"
                elif eval_group == "normal" and ground_truth == "SAFE":
                    ground_truth = "SAFE"
                elif eval_group == "normal" and ground_truth == "SUSPICIOUS":
                    ground_truth = "SUSPICIOUS"
                
                g["mis"].append({
                    "id": row_id,
                    "ground_truth": ground_truth,
                    "predicted": predicted,
                    "title": row.get('title', '')[:40]
                })
                print(f"  ✗ 错分: 期望={ground_truth}, 预测={predicted}")
        except Exception as e:
            print(f"  [错误] {e}")
            groups[eval_group]["total"] += 1
            groups[eval_group]["mis"].append({
                "id": row_id,
                "ground_truth": "UNKNOWN",
                "predicted": "ERROR",
                "title": row.get('title', '')[:40],
                "error": str(e)
            })
        
        time.sleep(0.5)
    
    # 输出结果
    print("\n" + "=" * 60)
    print("评测结果 (Agent V2 + RAG / 评测集_24条.csv)")
    print("=" * 60)
    
    total_all = 0
    correct_all = 0
    
    for group_name in ["normal", "insufficient", "not_listing"]:
        g = groups[group_name]
        if g["total"] == 0:
            continue
        accuracy = g["correct"] / g["total"] * 100