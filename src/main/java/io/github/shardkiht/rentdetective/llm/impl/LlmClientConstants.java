package io.github.shardkiht.rentdetective.llm.impl;

import okhttp3.MediaType;

/**
 * llm.impl 包常量。
 */
public final class LlmClientConstants {

    private LlmClientConstants() {
    }

    /** HTTP 请求 JSON 媒体类型 */
    public static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
}
