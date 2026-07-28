package io.github.shardkiht.rentdetective.agent.loop;

import java.util.Set;

/**
 * Agent 上下文管理（ThreadLocal）。
 * 用于在评测场景下传递 excludeIds，避免 RAG 检索泄题。
 */
public class AgentContext {

    private static final ThreadLocal<Set<Long>> EXCLUDE_IDS = new ThreadLocal<>();

    /**
     * 设置当前线程的 excludeIds（评测集 ID 集合）。
     */
    public static void setExcludeIds(Set<Long> ids) {
        EXCLUDE_IDS.set(ids);
    }

    /**
     * 获取当前线程的 excludeIds。
     */
    public static Set<Long> getExcludeIds() {
        return EXCLUDE_IDS.get();
    }

    /**
     * 清除当前线程的上下文。
     */
    public static void clear() {
        EXCLUDE_IDS.remove();
    }
}
