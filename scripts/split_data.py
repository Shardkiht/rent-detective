#!/usr/bin/env python3
"""
数据切分脚本：将104条数据按 80(案例库)/24(评测集) 切分。

约束条件：
1. 马甲组必须整体分到同一边，不能拆散：
   - 15505888755 (5条): IDs 32, 33, 40, 57, 58
   - 15068812900 (3条): IDs 37, 38, 82
   - 18200666391 (2条): IDs 89, 90
   - 微信:wjzdcs (2条): IDs 93, 95
2. 按 eval_group (normal/insufficient/not_listing) 分层比例切分
3. 输出两份CSV文件
"""

import csv
import io
import sys
from collections import defaultdict
from pathlib import Path

# 马甲组定义：按 phone/wechat 分组（已通过CSV实际验证）
MA_GROUPS = {
    "15505888755": {32, 33, 40, 57, 60},  # 5条
    "15068812900": {37, 38, 54},           # 3条
    "18200666391": {89, 90},                # 2条
    "微信:wjzdcs": {93, 95},                # 2条
}

# 马甲组所有ID集合
MA_IDS = set()
for ids in MA_GROUPS.values():
    MA_IDS.update(ids)

# 数据源
CSV_PATH = Path(__file__).parent.parent / "src" / "main" / "resources" / "杭州租房_104条_评测终版.csv"
OUTPUT_DIR = Path(__file__).parent.parent / "src" / "main" / "resources"

CASE_LIB_PATH = OUTPUT_DIR / "案例库_80条.csv"
EVAL_SET_PATH = OUTPUT_DIR / "评测集_24条.csv"

TARGET_CASE_LIB = 80
TARGET_EVAL_SET = 24


def read_csv(filepath: Path) -> list[dict]:
    """读取CSV文件，处理BOM和多行字段。"""
    with open(filepath, "r", encoding="utf-8-sig") as f:
        reader = csv.DictReader(f)
        rows = list(reader)
    return rows


def analyze(rows: list[dict]) -> dict:
    """分析数据分布。"""
    total = len(rows)
    group_counts = defaultdict(int)
    ma_group_counts = defaultdict(lambda: defaultdict(int))

    for row in rows:
        eval_group = row.get("eval_group", "normal").strip() or "normal"
        group_counts[eval_group] += 1

        row_id = int(row["id"])
        if row_id in MA_IDS:
            for ma_key, ids in MA_GROUPS.items():
                if row_id in ids:
                    ma_group_counts[ma_key][eval_group] += 1

    print(f"总条目数: {total}")
    print(f"\neval_group 分布:")
    for g in ["normal", "insufficient", "not_listing"]:
        count = group_counts.get(g, 0)
        pct = count / total * 100
        print(f"  {g}: {count} ({pct:.1f}%)")

    print(f"\n马甲组分布 (共 {len(MA_IDS)} 条):")
    for ma_key, ids in MA_GROUPS.items():
        print(f"  {ma_key}: {len(ids)} 条, eval_group: ", end="")
        for g, c in ma_group_counts[ma_key].items():
            print(f"{g}={c} ", end="")
        print()

    return {
        "total": total,
        "group_counts": dict(group_counts),
        "ma_group_counts": {k: dict(v) for k, v in ma_group_counts.items()},
    }


def split_data(rows: list[dict]) -> tuple[list[dict], list[dict]]:
    """
    按约束条件切分数据。

    策略：
    1. 先将马甲组全部放入80-set（案例库），保证RAG可以学习这些模式
    2. 剩余条目按 eval_group 分层，按比例分配到两个集合
    3. 检查最终分布是否合理
    """
    # 按马甲组和非马甲组分
    ma_rows = []
    non_ma_rows = []
    for row in rows:
        row_id = int(row["id"])
        if row_id in MA_IDS:
            ma_rows.append(row)
        else:
            non_ma_rows.append(row)

    print(f"\n马甲组条目: {len(ma_rows)} 条 (全部放入案例库)")
    print(f"非马甲组条目: {len(non_ma_rows)} 条")

    # 马甲组全部放入案例库
    case_lib_rows = list(ma_rows)
    # 评测集暂时为空
    eval_set_rows = []

    # 非马甲组需要分配到两个集合
    # 案例库还需要: 80 - len(ma_rows) 条
    # 评测集需要: 24 条
    case_lib_needed = TARGET_CASE_LIB - len(ma_rows)
    eval_set_needed = TARGET_EVAL_SET

    # 按 eval_group 分层非马甲组
    non_ma_by_group = defaultdict(list)
    for row in non_ma_rows:
        eval_group = row.get("eval_group", "normal").strip() or "normal"
        non_ma_by_group[eval_group].append(row)

    print(f"\n非马甲组按 eval_group 分布:")
    for g in ["normal", "insufficient", "not_listing"]:
        rows_in_group = non_ma_by_group.get(g, [])
        print(f"  {g}: {len(rows_in_group)} 条")

    # 计算目标比例（从整体数据推导）
    total_non_ma = len(non_ma_rows)
    target_distribution = {}
    for g in ["normal", "insufficient", "not_listing"]:
        count = len(non_ma_by_group.get(g, []))
        target_distribution[g] = {
            "count": count,
            "ratio": count / total_non_ma if total_non_ma > 0 else 0,
        }

    # 计算评测集（24条）中各组目标数量
    # 先算各组在整体中的比例，再乘以24
    # 但要考虑马甲组已经全部在案例库中（马甲组都是normal）
    # 所以案例库中normal已经多了，需要调整非马甲组的分配

    # 计算当前案例库中各组数量（已包含马甲组）
    case_lib_group_counts = defaultdict(int)
    for row in case_lib_rows:
        eval_group = row.get("eval_group", "normal").strip() or "normal"
        case_lib_group_counts[eval_group] += 1

    print(f"\n案例库（含马甲组）初始分布:")
    for g in ["normal", "insufficient", "not_listing"]:
        print(f"  {g}: {case_lib_group_counts.get(g, 0)} 条")

    # 目标：评测集24条的分布应大致反映整体分布
    # 整体分布（104条）:
    #   normal: 72 条 (69.2%)
    #   insufficient: 30 条 (28.8%)
    #   not_listing: 2 条 (1.9%)
    #
    # 但马甲组12条全是normal，且在案例库中
    # 所以需要从非马甲组中分配：
    #   - 案例库需要: 80 - 12 = 68 条非马甲
    #   - 评测集需要: 24 条非马甲
    #
    # 评测集24条中，按整体比例：
    #   normal: ~17 (69.2% * 24)
    #   insufficient: ~7 (28.8% * 24)
    #   not_listing: ~0.5 (1.9% * 24) -> 取 1
    #
    # 但非马甲组中各组数量是固定的，需要合理分配

    # 计算非马甲组中各组的数量
    non_ma_normal = len(non_ma_by_group.get("normal", []))
    non_ma_insufficient = len(non_ma_by_group.get("insufficient", []))
    non_ma_not_listing = len(non_ma_by_group.get("not_listing", []))

    print(f"\n非马甲组各组数量:")
    print(f"  normal: {non_ma_normal}")
    print(f"  insufficient: {non_ma_insufficient}")
    print(f"  not_listing: {non_ma_not_listing}")

    # 评测集目标分布（基于整体比例）
    # 104条中: normal=72, insufficient=30, not_listing=2
    # 比例: 69.2%, 28.8%, 1.9%
    # 24条中: normal=16.6~17, insufficient=6.9~7, not_listing=0.5~1

    # 但马甲组的12条都是normal，已经全在案例库
    # 非马甲组中normal的数量 = 72 - 12 = 60
    # 非马甲组中insufficient的数量 = 30
    # 非马甲组中not_listing的数量 = 2

    # 分配策略：
    # 评测集24条，按比例从非马甲组中抽取
    # 案例库68条，从非马甲组中抽取剩余的

    # 计算评测集从每组抽取的数量
    # 目标比例保持和整体一致
    total_non_ma_for_split = non_ma_normal + non_ma_insufficient + non_ma_not_listing
    # 从非马甲组中按比例抽取24条到评测集
    eval_normal = max(1, round(non_ma_normal / total_non_ma_for_split * eval_set_needed))
    eval_insufficient = max(1, round(non_ma_insufficient / total_non_ma_for_split * eval_set_needed))
    eval_not_listing = max(0, round(non_ma_not_listing / total_non_ma_for_split * eval_set_needed))

    # 调整总数到24
    total_calc = eval_normal + eval_insufficient + eval_not_listing
    diff = eval_set_needed - total_calc
    # 将差值加到最大的组
    if diff != 0:
        eval_normal += diff

    # 确保不超过可用数量
    eval_normal = min(eval_normal, non_ma_normal)
    eval_insufficient = min(eval_insufficient, non_ma_insufficient)
    eval_not_listing = min(eval_not_listing, non_ma_not_listing)

    # 再次检查总数
    total_assigned = eval_normal + eval_insufficient + eval_not_listing
    if total_assigned != eval_set_needed:
        # 调整
        diff = eval_set_needed - total_assigned
        if diff > 0:
            # 需要更多，从最多的组加
            remaining_normal = non_ma_normal - eval_normal
            remaining_insufficient = non_ma_insufficient - eval_insufficient
            if remaining_normal >= diff:
                eval_normal += diff
            elif remaining_insufficient >= diff:
                eval_insufficient += diff
            else:
                eval_normal += remaining_normal
                eval_insufficient += remaining_insufficient
                remaining = diff - remaining_normal - remaining_insufficient
                eval_not_listing = min(eval_not_listing + remaining, non_ma_not_listing)
        elif diff < 0:
            # 需要减少
            eval_normal += diff  # 从normal减

    print(f"\n评测集目标分布: normal={eval_normal}, insufficient={eval_insufficient}, not_listing={eval_not_listing}")

    # 案例库从非马甲组中抽取剩余的
    lib_normal = non_ma_normal - eval_normal
    lib_insufficient = non_ma_insufficient - eval_insufficient
    lib_not_listing = non_ma_not_listing - eval_not_listing

    print(f"案例库(非马甲)目标分布: normal={lib_normal}, insufficient={lib_insufficient}, not_listing={lib_not_listing}")

    # 验证总数
    total_lib = len(ma_rows) + lib_normal + lib_insufficient + lib_not_listing
    total_eval = eval_normal + eval_insufficient + eval_not_listing
    print(f"\n案例库总数: {total_lib} (目标{TARGET_CASE_LIB})")
    print(f"评测集总数: {total_eval} (目标{TARGET_EVAL_SET})")

    # 按ID排序，取前N个（保证可复现，不用随机）
    # 先按ID排序
    for g in ["normal", "insufficient", "not_listing"]:
        non_ma_by_group[g].sort(key=lambda r: int(r["id"]))

    # 分配评测集
    eval_set_rows.extend(non_ma_by_group["normal"][:eval_normal])
    eval_set_rows.extend(non_ma_by_group["insufficient"][:eval_insufficient])
    eval_set_rows.extend(non_ma_by_group["not_listing"][:eval_not_listing])

    # 分配案例库（非马甲部分）
    case_lib_rows.extend(non_ma_by_group["normal"][eval_normal:])
    case_lib_rows.extend(non_ma_by_group["insufficient"][eval_insufficient:])
    case_lib_rows.extend(non_ma_by_group["not_listing"][eval_not_listing:])

    # 按ID排序
    case_lib_rows.sort(key=lambda r: int(r["id"]))
    eval_set_rows.sort(key=lambda r: int(r["id"]))

    return case_lib_rows, eval_set_rows


def write_csv(filepath: Path, rows: list[dict], original_headers: list[str]):
    """写入CSV文件。"""
    with open(filepath, "w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=original_headers)
        writer.writeheader()
        writer.writerows(rows)
    print(f"已写入: {filepath} ({len(rows)} 条)")


def verify_split(case_lib: list[dict], eval_set: list[dict]):
    """验证切分结果。"""
    print(f"\n{'='*60}")
    print("验证切分结果")
    print(f"{'='*60}")

    # 检查ID不重叠
    case_ids = {int(r["id"]) for r in case_lib}
    eval_ids = {int(r["id"]) for r in eval_set}
    overlap = case_ids & eval_ids
    assert len(overlap) == 0, f"ID重叠: {overlap}"
    print(f"✓ ID无重叠")

    # 检查总数
    total = len(case_lib) + len(eval_set)
    print(f"✓ 案例库: {len(case_lib)} 条, 评测集: {len(eval_set)} 条, 总计: {total}")

    # 检查马甲组完整性
    for ma_key, ids in MA_GROUPS.items():
        case_ma = ids & case_ids
        eval_ma = ids & eval_ids
        assert len(case_ma) == len(ids) or len(eval_ma) == len(ids), \
            f"马甲组 {ma_key} 被拆散: 案例库{len(case_ma)}条, 评测集{len(eval_ma)}条"
        side = "案例库" if len(case_ma) == len(ids) else "评测集"
        print(f"✓ 马甲组 {ma_key} ({len(ids)}条) 完整在{side}")

    # 检查eval_group分布
    print(f"\n案例库 eval_group 分布:")
    for g in ["normal", "insufficient", "not_listing"]:
        count = sum(1 for r in case_lib if r.get("eval_group", "").strip() == g)
        pct = count / len(case_lib) * 100
        print(f"  {g}: {count} ({pct:.1f}%)")

    print(f"\n评测集 eval_group 分布:")
    for g in ["normal", "insufficient", "not_listing"]:
        count = sum(1 for r in eval_set if r.get("eval_group", "").strip() == g)
        pct = count / len(eval_set) * 100
        print(f"  {g}: {count} ({pct:.1f}%)")

    # 对比整体分布
    print(f"\n整体(104条) eval_group 分布:")
    total_all = len(case_lib) + len(eval_set)
    for g in ["normal", "insufficient", "not_listing"]:
        count_case = sum(1 for r in case_lib if r.get("eval_group", "").strip() == g)
        count_eval = sum(1 for r in eval_set if r.get("eval_group", "").strip() == g)
        total_g = count_case + count_eval
        pct = total_g / total_all * 100
        print(f"  {g}: {total_g} ({pct:.1f}%)")

    # 评测集分布 vs 整体分布差异
    print(f"\n评测集 vs 整体 分布差异:")
    for g in ["normal", "insufficient", "not_listing"]:
        count_eval = sum(1 for r in eval_set if r.get("eval_group", "").strip() == g)
        eval_pct = count_eval / len(eval_set) * 100
        count_total = sum(1 for r in (case_lib + eval_set) if r.get("eval_group", "").strip() == g)
        total_pct = count_total / total_all * 100
        diff = eval_pct - total_pct
        print(f"  {g}: 评测集{eval_pct:.1f}% vs 整体{total_pct:.1f}% (差异{diff:+.1f}%)")

    return True


def main():
    print("=" * 60)
    print("RentDetective 数据切分脚本")
    print("=" * 60)

    # 读取数据
    rows = read_csv(CSV_PATH)
    print(f"\n读取CSV: {CSV_PATH}")
    print(f"共 {len(rows)} 条数据")

    # 分析
    stats = analyze(rows)

    # 切分
    case_lib, eval_set = split_data(rows)

    # 验证
    verify_split(case_lib, eval_set)

    # 获取原始CSV的header
    if rows:
        original_headers = list(rows[0].keys())
    else:
        original_headers = []

    # 写入文件
    print(f"\n{'='*60}")
    write_csv(CASE_LIB_PATH, case_lib, original_headers)
    write_csv(EVAL_SET_PATH, eval_set, original_headers)

    print(f"\n{'='*60}")
    print("切分完成！")
    print(f"  案例库: {CASE_LIB_PATH}")
    print(f"  评测集: {EVAL_SET_PATH}")


if __name__ == "__main__":
    main()