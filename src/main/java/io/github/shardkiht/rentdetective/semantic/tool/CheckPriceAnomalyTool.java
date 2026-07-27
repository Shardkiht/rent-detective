package io.github.shardkiht.rentdetective.semantic.tool;

import io.github.shardkiht.rentdetective.agent.tool.Tool;
import io.github.shardkiht.rentdetective.agent.tool.ToolResult;
import org.springframework.stereotype.Component;

/**
 * 价格偏离度比对工具。
 * TODO: 价格基线功能待实现——需要按区域/户型建立价格基线后，才能判断偏离度。
 */
@Component
public class CheckPriceAnomalyTool implements Tool {

    @Override
    public String name() {
        return "check_price_anomaly";
    }

    @Override
    public String description() {
        return "检查房源价格是否偏离同区域/同户型的市场基线。" +
                "输入价格和位置信息，返回偏离程度和判断。" +
                "参数：price（必填，房源价格数字）、location（可选，位置描述）。" +
                "注意：当前为占位实现，价格基线功能待后续版本提供。";
    }

    @Override
    public String argsJsonSchema() {
        return """
                {"type":"object","properties":{"price":{"type":"number","description":"房源价格（元/月）"},"location":{"type":"string","description":"位置描述（可选）"}},"required":["price"]}""";
    }

    @Override
    public ToolResult execute(String argsJson) {
        // TODO: 价格基线功能待实现——需要按区域/户型建立价格基线后，才能判断偏离度
        return ToolResult.ok("{\"message\": \"价格基线功能待实现\"}");
    }
}
