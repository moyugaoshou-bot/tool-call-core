package com.toolcall.model;

import java.util.Map;

/**
 * LLM 返回的工具调用请求
 */
public record ToolCall(
    String id,
    String name,
    Map<String, Object> arguments
) {}
