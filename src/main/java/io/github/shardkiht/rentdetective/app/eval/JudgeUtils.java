package io.github.shardkiht.rentdetective.app.eval;

/**
 * 三方案共用的判定逻辑。
 * 
 * 口径规则：
 * - normal 组：humanLabel=safe → 仅 SAFE 算对；humanLabel=suspicious → 仅 SUSPICIOUS 算对
 * - insufficient 组：verdict ∈ {INSUFFICIENT, REVIEW} 算对
 * - not_listing 组：verdict == NOT_LISTING 算对
 */
public final class JudgeUtils {

    private JudgeUtils() {}

    /**
     * 判断预测是否正确。
     * 
     * @param group 分组名（normal / insufficient / info_insufficient / not_listing）
     * @param humanLabel 人工标注（safe / suspicious 等）
     * @param predicted 模型预测（SAFE / SUSPICIOUS / REVIEW / INSUFFICIENT / NOT_LISTING）
     * @return 是否正确
     */
    public static boolean judgeCorrect(String group, String humanLabel, String predicted) {
        if (predicted == null || "ERROR".equals(predicted) || "UNKNOWN".equals(predicted)) {
            return false;
        }
        String pred = predicted.toUpperCase();

        return switch (group) {
            case "normal" -> {
                String label = humanLabel != null ? humanLabel.toLowerCase() : "";
                yield switch (label) {
                    case "safe" -> "SAFE".equals(pred);
                    case "suspicious" -> "SUSPICIOUS".equals(pred);
                    default -> false;
                };
            }
            case "insufficient", "info_insufficient" ->
                    "INSUFFICIENT".equals(pred) || "REVIEW".equals(pred);
            case "not_listing" -> "NOT_LISTING".equals(pred);
            default -> false;
        };
    }

    /**
     * 判断预测是否为 REVIEW。
     */
    public static boolean isReview(String predicted) {
        return "REVIEW".equals(predicted);
    }
}
