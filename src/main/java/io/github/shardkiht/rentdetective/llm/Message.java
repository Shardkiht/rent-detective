package io.github.shardkiht.rentdetective.llm;

import java.util.List;

public record Message(String role, String content, String name) {

    public static Message system(String content) {
        return new Message("system", content, null);
    }

    public static Message user(String content) {
        return new Message("user", content, null);
    }

    public static Message assistant(String content) {
        return new Message("assistant", content, null);
    }

    public static Message tool(String name, String content) {
        return new Message("tool", content, name);
    }

    public static Message of(String role, String content) {
        return new Message(role, content, null);
    }
}
