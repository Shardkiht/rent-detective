#!/usr/bin/env python3
"""
Agent + RAG 独立评测脚本 —— 在 24 条评测集上跑 ReAct Agent + RAG。
RAG 检索池为 80 条案例库（已排除 24 条评测集，避免泄题）。
无需 Spring Boot / MySQL / Redis，仅需 SiliconFlow API Key。
"""

import csv
import json
import math
import os
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

MAX_STEPS = 8  # 与 Java 侧保持一致
MIN_DISTINCT_TOOLS = 2  # 与 Java 侧保持一致

VALID_VERDICTS = {"SAFE", "SUSPICIOUS", "REVIEW", "INSUFFICIENT", "NOT_LISTING"}

# ==================== System Prompt ====================
SYSTEM_PROMPT = """你是一个租房风险调查侦探，任务是分析给定房源信息，判断是否存在风险。你可以调用工具获取更多信息辅助判断。

【可用工具】
1. analyze_description: 对房源描述进行话术套路检测（基于规则引擎），返回 SAFE/SUSPICIOUS/REVIEW/INSUFFICIENT/NOT_LISTING 判定
   - 参数: description (必填), title (可选), price (可选), location (可选)
   
2. search_similar_listings: 在案例库中检索相似案例
   - 参数: description (必填)
   
3. check_price_anomaly: 检查价格是否异常
   - 参数: price (必填), description (可选)

【推荐调查策略】
1. 首先调用 analyze_description 获取规则引擎的确定性判断（这是最可靠的）
2. 再根据需要调用 search_similar_listings 或 check_price_anomaly 补充证据
3. 至少调用 2 个不同工具后才能给出结论

【判断标准】
- SAFE: 信息具体真实、价格合理、有可验证细节
- SUSPICIOUS: 存在明显欺诈特征（话术套路、信息矛盾、价格异常、中介伪装）
- REVIEW: 有疑点但证据不充分
- INSUFFICIENT: 关键信息严重缺失
- NOT_LISTING: 内容根本不是房源信息

【输出格式】
{"verdict": "五选一", "confidence": 0到1之间的数字, "evidences": [{"claim": "证据描述", "sourceTool": "工具名", "quote": "引用内容"}]}
"""

# ==================== Embedding ====================
def get_embedding(text):
    """调用 embedding API。"""
    payload = {
        "model": EMBEDDING_MODEL,
        "input": text
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
            return data["data"][0]["embedding"]
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
    # 读取案例库
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
    
    # 缓存
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
            "score": round(score, 4),
            "reason": build_reason(score, m.get("phone", ""), "")
        })
    return results

def build_reason(score, phone, query_phone):
    """生成检索原因。"""
    if score >= 0.85:
        return "高度相似"
    if query_phone and phone and query_phone == phone:
        return f"与案例联系方式相同"
    return "文本内容相似"

# ==================== 工具实现 ====================
def tool_search_similar(args, vectors, meta, exclude_ids):
    """search_similar_listings 工具。"""
    description = args.get("description") or args.get("text", "")
    if not description:
        return {"success": False, "error": "缺少参数: description"}
    
    cases = search_similar(description, vectors, meta, top_k=5, exclude_ids=exclude_ids)
    return {"success": True, "data": json.dumps(cases, ensure_ascii=False)}

def tool_check_price_anomaly(args, vectors, meta, exclude_ids):
    """check_price_anomaly 工具。"""
    price = args.get("price")
    if price is None or price <= 0:
        return {"success": True, "data": json.dumps({
            "verdict": "ANOMALY",
            "inputPrice": price,
            "message": "价格 ≤ 0，明显异常"
        }, ensure_ascii=False)}
    
    description = args.get("description", "")
    location = args.get("location", "")
    search_text = description or location
    if not search_text:
        return {"success": True, "data": json.dumps({
            "verdict": "UNKNOWN",
            "inputPrice": price,
            "message": "缺少描述文本，无法进行相似案例检索"
        }, ensure_ascii=False)}
    
    # 检索 top-10 相似案例
    cases = search_similar(search_text, vectors, meta, top_k=10, exclude_ids=exclude_ids)
    
    # 过滤：相似度 > 0.6 且有有效价格
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
            "message": f"可比样本不足（仅 {len(comparable_prices)} 条，需 ≥ 3），无法判断"
        }, ensure_ascii=False)}
    
    # 计算中位数
    comparable_prices.sort()
    n = len(comparable_prices)
    if n % 2 == 0:
        median = (comparable_prices[n//2 - 1] + comparable_prices[n//2]) / 2.0
    else:
        median = comparable_prices[n//2]
    
    deviation = (price - median) / median
    if abs(deviation) > 0.35:
        verdict = "ANOMALY"
        message = f"价格 {price} 元偏离相似案例中位数 {median:.0f} 元达 {deviation*100:.1f}%（阈值 ±35%）"
    else:
        verdict = "NORMAL"
        message = f"价格 {price} 元处于相似案例中位数 {median:.0f} 元的合理区间（偏离 {deviation*100:.1f}%）"
    
    return {"success": True, "data": json.dumps({
        "verdict": verdict,
        "inputPrice": price,
        "comparableCount": len(comparable_prices),
        "medianPrice": round(median, 2),
        "deviation": round(deviation, 4),
        "message": message
    }, ensure_ascii=False)}

# ==================== 规则引擎模拟（analyze_description 工具） ====================
# 基于 Java RuleEngine 的核心逻辑，提供确定性的规则判断

# 非房源标题关键词
NOT_LISTING_KEYWORDS = ["求租", "找室友", "值得嘛"]

# 中介套路关键词（来自 AgentStockPhraseMatcher 等）
SUSPICIOUS_PATTERNS = {
    "agent_stock_phrase": {
        "keywords": ["无中介费", "免中介费", "无任何费用", "房东直签", "直签", "直接签"],
        "weight": 0.2,
        "description": "中介套话"
    },
    "contact_spam": {
        "keywords": ["加微信", "加V", "加vx", "微信", "联系", "看房", "联系方式"],
        "weight": 0.15,
        "description": "引流联系方式"
    },
    "over_denial": {
        "keywords": ["不是中介", "个人房东", "业主本人", "非中介", "本人"],
        "weight": 0.15,
        "description": "过度否认"
    },
    "coverage_language": {
        "keywords": ["等等", "等", "等"],
        "weight": 0.05,
        "description": "话术覆盖"
    },
    "sales_over_substance": {
        "keywords": ["拎包入住", "家电齐全", "家具家电", "精装修", "豪华装修"],
        "weight": 0.1,
        "description": "销售话术多于实质"
    }
}

def rule_engine_analyze(title, description, price, location):
    """
    简化版规则引擎分析，模拟 Java RuleEngine 的核心判断逻辑。
    返回: {verdict, score, hits, advice, reason}
    """
    title = title or ""
    description = description or ""
    text = f"{title} {description}"
    text_lower = text.lower()
    
    # === 第一步：not_listing 判断 ===
    for kw in NOT_LISTING_KEYWORDS:
        if kw in title:
            return {
                "verdict": "NOT_LISTING",
                "score": 0.0,
                "hits": [],
                "advice": ["这不是一个出租房源信息"],
                "reason": f"标题含非房源关键词: {kw}"
            }
    
    # === 第二步：info_insufficient 判断 ===
    missing_items = []
    if not price or price <= 0:
        missing_items.append("价格")
    if not location or len(location.strip()) < 2:
        missing_items.append("位置")
    if len(description.strip()) < 20:
        missing_items.append("详细描述")
    
    if len(missing_items) >= 2:
        return {
            "verdict": "INSUFFICIENT",
            "score": 0.0,
            "hits": [],
            "advice": [f"关键信息缺失: {', '.join(missing_items)}"],
            "reason": f"缺少 {len(missing_items)} 项关键信息: {', '.join(missing_items)}"
        }
    
    # === 第三步：加权打分 ===
    hits = []
    score = 0.0
    
    for rule_type, config in SUSPICIOUS_PATTERNS.items():
        for keyword in config["keywords"]:
            if keyword in text_lower:
                hits.append({
                    "ruleType": rule_type,
                    "weight": config["weight"],
                    "evidence": f"命中关键词: '{keyword}'",
                    "description": config["description"]
                })
                score += config["weight"]
                break  # 同一规则类型只算一次
    
    # 价格异常额外加分
    if price and price > 0:
        if price < 500:
            score += 0.2
            hits.append({
                "ruleType": "low_price",
                "weight": 0.2,
                "evidence": f"价格 {price} 元低于常见市场价",
                "description": "低价陷阱"
            })
        elif price > 8000:
            score += 0.15
            hits.append({
                "ruleType": "high_price",
                "weight": 0.15,
                "evidence": f"价格 {price} 元高于常见市场价",
                "description": "高租金异常"
            })
    
    # === 第四步：根据分数判定 ===
    SUSPICIOUS_THRESHOLD = 0.6
    REVIEW_THRESHOLD = 0.4
    
    if score >= SUSPICIOUS_THRESHOLD:
        verdict = "SUSPICIOUS"
    elif score >= REVIEW_THRESHOLD:
        verdict = "REVIEW"
    else:
        verdict = "SAFE"
    
    advice = []
    if verdict == "SUSPICIOUS":
        advice.append("存在明显风险特征，建议谨慎核实")
    elif verdict == "REVIEW":
        advice.append("有疑点但证据不充分，建议人工复核")
    else:
        advice.append("信息完整，未发现明显风险特征")
    
    return {
        "verdict": verdict,
        "score": round(score, 2),
        "hits": hits,
        "advice": advice,
        "reason": f"累计风险分数 {score:.2f}（阈值: suspicious={SUSPICIOUS_THRESHOLD}, review={REVIEW_THRESHOLD}）"
    }

def tool_analyze_description(args, vectors, meta, exclude_ids):
    """analyze_description 工具 - 话术套路检测。"""
    description = args.get("description", "")
    if not description:
        return {"success": False, "error": "缺少参数: description"}
    
    title = args.get("title", "")
    price = args.get("price")
    if price is not None:
        try:
            price = float(price)
        except (ValueError, TypeError):
            price = None
    else:
        price = None
    location = args.get("location", "")
    
    result = rule_engine_analyze(title, description, price, location)
    return {"success": True, "data": json.dumps(result, ensure_ascii=False)}

# ==================== Agent Loop ====================
def call_llm_agent(messages, tools):
    """调用 LLM，支持工具调用。"""
    payload = {
        "model": LLM_MODEL,
        "temperature": 0.3,
        "messages": messages,
        "tools": tools
    }
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
                timeout=120
            )
            resp.raise_for_status()
            data = resp.json()
            return data["choices"][0]["message"]
        except Exception as e:
            if attempt < 2:
                time.sleep(2 ** attempt)
            else:
                raise RuntimeError(f"LLM 调用失败: {e}")

def run_agent(listing, vectors, meta, exclude_ids):
    """运行 Agent 调查循环。"""
    listing_info = f"""请调查以下房源信息：
标题: {listing.get('title', '')}
价格: {listing.get('price', '')} 元/月
位置: {listing.get('location', '')}
描述: {listing.get('description', '')}
联系电话: {listing.get('phone', '无')}
"""
    
    messages = [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": listing_info}
    ]
    
    # 定义工具（与 Java 侧保持一致）
    tools = [
        {
            "type": "function",
            "function": {
                "name": "analyze_description",
                "description": "对房源描述进行话术套路检测，基于规则引擎进行识坑分析。返回判定结果（SAFE/SUSPICIOUS/REVIEW/INSUFFICIENT/NOT_LISTING）、风险分数、命中规则。",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "description": {"type": "string", "description": "房源描述全文"},
                        "title": {"type": "string", "description": "房源标题（可选）"},
                        "price": {"type": "number", "description": "标注价格（可选）"},
                        "location": {"type": "string", "description": "位置描述（可选）"}
                    },
                    "required": ["description"]
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "search_similar_listings",
                "description": "在案例库中检索与当前房源描述最相似的案例。返回 Top-5 相似案例。",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "description": {"type": "string", "description": "房源描述文本"},
                        "text": {"type": "string", "description": "备选：同 description"}
                    },
                    "required": ["description"]
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "check_price_anomaly",
                "description": "检查房源价格是否偏离相似案例的市场水平。",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "price": {"type": "number", "description": "房源价格（元/月）"},
                        "description": {"type": "string", "description": "房源描述文本"},
                        "location": {"type": "string", "description": "位置描述"}
                    },
                    "required": ["price"]
                }
            }
        }
    ]
    
    tools_called = set()
    
    for step in range(1, MAX_STEPS + 1):
        response = call_llm_agent(messages, tools)
        
        # 检查是否有工具调用
        tool_calls = response.get("tool_calls", [])
        
        if tool_calls:
            for tc in tool_calls:
                fn_name = tc["function"]["name"]
                fn_args = json.loads(tc["function"]["arguments"])
                
                tools_called.add(fn_name)
                
                # 执行工具
                if fn_name == "analyze_description":
                    result = tool_analyze_description(fn_args, vectors, meta, exclude_ids)
                elif fn_name == "search_similar_listings":
                    result = tool_search_similar(fn_args, vectors, meta, exclude_ids)
                elif fn_name == "check_price_anomaly":
                    result = tool_check_price_anomaly(fn_args, vectors, meta, exclude_ids)
                else:
                    result = {"success": False, "error": f"未知工具: {fn_name}"}
                
                # 添加到消息
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
            # LLM 给出了文本回复（可能是结论）
            content = response.get("content", "")
            
            # 尝试解析结论
            verdict = try_parse_verdict(content)
            
            if verdict and len(tools_called) >= MIN_DISTINCT_TOOLS:
                return verdict
            
            if verdict:
                # 工具调用不足，拒绝结论
                messages.append({"role": "assistant", "content": content})
                messages.append({"role": "user", "content": 
                    f"你目前只调用了 {len(tools_called)} 个工具（{tools_called}），证据不充分。"
                    f"必须实际调用至少 {MIN_DISTINCT_TOOLS} 个不同工具后才能给出结论。"
                    f"请继续调用其他工具获取更多信息。"})
            else:
                # 格式不对
                messages.append({"role": "assistant", "content": content})
                messages.append({"role": "user", "content": 
                    "你的回答不是合法的 JSON 结论格式，请严格按照要求的格式重新输出。"})
    
    # 达到步数上限，强制收敛
    messages.append({"role": "user", "content": 
        "已达到最大调查步数，请基于目前已获得的信息直接给出结论 JSON，不要再请求调用任何工具。"})
    
    # 不传工具
    final_response = call_llm_agent(messages, [])
    content = final_response.get("content", "")
    verdict = try_parse_verdict(content)
    
    if verdict:
        return verdict
    # 兜底
    return "SUSPICIOUS"

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
def build_ground_truth(row):
    """确定正确答案（与 Java JudgeUtils.judgeCorrect 对应）。"""
    eval_group = row.get("eval_group", "normal").strip()
    human_label = row.get("risk_level", "").strip().lower()
    
    if eval_group == "normal":
        if human_label == "safe":
            return "SAFE"
        elif human_label == "suspicious":
            return "SUSPICIOUS"
        return "UNKNOWN"
    elif eval_group in ("insufficient", "info_insufficient"):
        return "INSUFFICIENT"
    elif eval_group == "not_listing":
        return "NOT_LISTING"
    return "UNKNOWN"

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
    print("Agent + RAG 独立评测 —— 评测集_24条.csv")
    print("RAG 检索池: 案例库_80条.csv (排除评测集 ID，避免泄题)")
    print("=" * 60)
    
    # 加载案例库向量
    vectors, meta = load_or_build_vectors()
    print(f"案例库向量: {len(vectors)} 条")
    
    # 读取评测集
    eval_rows = []
    with open(EVAL_CSV, "r", encoding="utf-8-sig") as f:
        eval_rows = list(csv.DictReader(f))
    
    print(f"评测集: {len(eval_rows)} 条")
    
    # 评测集 ID 集合（用于 RAG 池排除）
    eval_ids = set(int(r["id"]) for r in eval_rows)
    print(f"评测集 IDs: {sorted(eval_ids)}")
    print(f"RAG 池排除 {len(eval_ids)} 个 ID，剩余 {len(vectors) - len([k for k in vectors if int(k) in eval_ids])} 条")
    
    # 分组统计
    groups = defaultdict(lambda: {"total": 0, "correct": 0, "review": 0, "mis": []})
    
    for i, row in enumerate(eval_rows):
        row_id = int(row["id"])
        eval_group = row.get("eval_group", "normal").strip()
        
        print(f"\n{'─'*50}")
        print(f"[{i+1}/{len(eval_rows)}] ID={row_id}, group={eval_group}")
        print(f"  标题: {row.get('title', '')[:60]}...")
        
        try:
            predicted = run_agent(row, vectors, meta, eval_ids)
            correct = judge_correct(row, predicted)
            
            g = groups[eval_group]
            g["total"] += 1
            if predicted == "REVIEW":
                g["review"] += 1
            if correct:
                g["correct"] += 1
                print(f"  ✓ 预测={predicted}")
            else:
                ground_truth = build_ground_truth(row)
                g["mis"].append({
                    "id": row_id,
                    "ground_truth": ground_truth,
                    "predicted": predicted,
                    "title": row.get("title", "")[:40]
                })
                print(f"  ✗ 错分: 期望={ground_truth}, 预测={predicted}")
        except Exception as e:
            print(f"  [异常] {e}")
            g = groups[eval_group]
            g["total"] += 1
            g["mis"].append({
                "id": row_id,
                "ground_truth": build_ground_truth(row),
                "predicted": "ERROR",
                "title": row.get("title", "")[:40]
            })
        
        # 休息避免限流
        time.sleep(1)
    
    # 输出结果
    print("\n" + "=" * 60)
    print("评测结果 (Agent + RAG / 评测集_24条.csv)")
    print("=" * 60)
    
    total_all = 0
    correct_all = 0
    
    for group_name in ["normal", "insufficient", "not_listing"]:
        g = groups[group_name]
        if g["total"] == 0:
            continue
        accuracy = g["correct"] / g["total"] * 100
        review_rate = g["review"] / g["total"] * 100
        total_all += g["total"]
        correct_all += g["correct"]
        
        print(f"\n--- {group_name} 组 ---")
        print(f"  总数: {g['total']}, 正确: {g['correct']}, 准确率: {accuracy:.1f}%")
        if group_name == "normal":
            print(f"  REVIEW率: {review_rate:.1f}%")
        if g["mis"]:
            print(f"  错分案例:")
            for m in g["mis"]:
                print(f"    ID={m['id']}, 期望={m['ground_truth']}, 预测={m['predicted']}, 标题={m['title']}")
    
    overall = correct_all / total_all * 100 if total_all > 0 else 0
    print(f"\n=== 总体准确率: {overall:.1f}% ({correct_all}/{total_all}) ===")

if __name__ == "__main__":
    main()