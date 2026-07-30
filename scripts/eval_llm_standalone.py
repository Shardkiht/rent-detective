#!/usr/bin/env python3
"""
纯 LLM 独立评测脚本 —— 在 24 条评测集上跑单轮 LLM 判断。
无需 Spring Boot / MySQL / Redis，仅需 SiliconFlow API Key。
"""

import csv
import json
import os
import time
import requests
import sys
from collections import defaultdict
from pathlib import Path

# ==================== 配置 ====================
API_BASE = "https://api.siliconflow.cn/v1"
API_KEY = os.environ.get("SILICONFLOW_API_KEY", "")
if not API_KEY:
    print("⚠️  警告：环境变量 SILICONFLOW_API_KEY 未设置", file=sys.stderr)
MODEL = "deepseek-ai/DeepSeek-V3.1-Terminus"

RESOURCE_DIR = Path(__file__).parent.parent / "src" / "main" / "resources"
EVAL_CSV = RESOURCE_DIR / "eval" / "评测集_24条.csv"

# ==================== Prompt ====================
# 使用 %s 占位符避免花括号冲突
LLM_EVAL_PROMPT_TEMPLATE = """你是一个租房风险判断专家。根据以下房源信息，判断其风险等级。
只输出一个 JSON 对象，不要输出其他任何文字：
{"verdict": "五选一", "confidence": 0到1之间的数字}

verdict 只能是以下五个值之一：
- SAFE：信息具体真实、价格合理、有可验证细节，无风险
- SUSPICIOUS：存在明显欺诈特征（话术套路、信息矛盾、价格异常、中介伪装）
- REVIEW：有疑点但证据不充分，建议人工复核
- INSUFFICIENT：关键信息严重缺失（无价格/无位置/无描述），无法做出有效判断
- NOT_LISTING：内容根本不是房源信息（广告、招聘、无关内容等）

判断标准：
- 只要标题或描述中有具体小区名、具体价格、具体位置，就不能判 INSUFFICIENT
- 标题是"找室友""求租"这类不是出租房源的，判 NOT_LISTING
- 描述中有微信号/电话引流、大量房源汇总、价格明显低于市场价的，判 SUSPICIOUS
- 信息完整但无明显问题的，判 SAFE

示例：
标题: 余杭区草荡苑小区，房东直租地铁19号线海创园站
描述: 三室两厅，朝南带阳台，民用水电，房东直租无中介费
价格: 4500 元/月
位置: 余杭区
→ {"verdict": "SAFE", "confidence": 0.9}

标题: 杭州无中介费租房、2号线5号线、大量好房!
描述: 加微信看房，房源多多，价格优惠
价格: 1500 元/月
位置: 杭州
→ {"verdict": "SUSPICIOUS", "confidence": 0.85}

标题: 杭州上城找室友
描述: 本人女，想在上城区找合租室友
价格: 未知 元/月
位置: 上城区
→ {"verdict": "NOT_LISTING", "confidence": 0.9}

房源信息：
标题: %s
描述: %s
价格: %s 元/月
位置: %s
"""

VALID_VERDICTS = {"SAFE", "SUSPICIOUS", "REVIEW", "INSUFFICIENT", "NOT_LISTING"}

# 评测集 -> ground truth 映射（基于 risk_level + eval_group）
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

def call_llm(title, description, price, location):
    """调用 LLM API，返回 verdict。"""
    prompt = LLM_EVAL_PROMPT_TEMPLATE % (
        title or "",
        description or "",
        price if price else "未知",
        location or ""
    )
    
    payload = {
        "model": MODEL,
        "temperature": 0.1,
        "messages": [{"role": "user", "content": prompt}]
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
                timeout=90
            )
            resp.raise_for_status()
            data = resp.json()
            content = data["choices"][0]["message"]["content"]
            return parse_verdict(content)
        except Exception as e:
            if attempt < 2:
                print(f"  [重试 {attempt+1}/3] {e}")
                time.sleep(2 ** attempt)
            else:
                print(f"  [失败] {e}")
                return "ERROR"
    return "ERROR"

def parse_verdict(content):
    """从 LLM 返回中解析 verdict。"""
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
    
    # 兜底：关键词匹配
    upper = content.upper()
    if "NOT_LISTING" in upper or "NOT LISTING" in upper:
        return "NOT_LISTING"
    if "INSUFFICIENT" in upper:
        return "INSUFFICIENT"
    if "SUSPICIOUS" in upper:
        return "SUSPICIOUS"
    if "REVIEW" in upper:
        return "REVIEW"
    if "SAFE" in upper:
        return "SAFE"
    return "UNKNOWN"

def main():
    print("=" * 60)
    print("纯 LLM 独立评测 —— 评测集_24条.csv")
    print("=" * 60)
    
    # 读取评测集
    rows = []
    with open(EVAL_CSV, "r", encoding="utf-8-sig") as f:
        reader = csv.DictReader(f)
        rows = list(reader)
    
    print(f"读取 {len(rows)} 条评测数据")
    
    # 按 eval_group 分组统计
    groups = defaultdict(lambda: {"total": 0, "correct": 0, "review": 0, "mis": []})
    
    for i, row in enumerate(rows):
        row_id = int(row["id"])
        title = row.get("title", "")
        description = row.get("description", "")
        price = row.get("price", "")
        location = row.get("location", "")
        eval_group = row.get("eval_group", "normal").strip()
        
        print(f"\n[{i+1}/{len(rows)}] ID={row_id}, group={eval_group}")
        print(f"  标题: {title[:60]}...")
        
        predicted = call_llm(title, description, price, location)
        correct = judge_correct(row, predicted)
        
        g = groups[eval_group]
        g["total"] += 1
        if predicted == "REVIEW":
            g["review"] += 1
        if correct:
            g["correct"] += 1
        else:
            ground_truth = build_ground_truth(row)
            g["mis"].append({
                "id": row_id,
                "ground_truth": ground_truth,
                "predicted": predicted,
                "title": title[:40]
            })
            print(f"  错分: 期望={ground_truth}, 预测={predicted}")
        
        # 避免 API 限流
        time.sleep(0.5)
    
    # 输出结果
    print("\n" + "=" * 60)
    print("评测结果 (纯 LLM / 评测集_24条.csv)")
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