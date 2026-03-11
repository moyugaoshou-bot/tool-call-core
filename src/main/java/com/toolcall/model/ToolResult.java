package com.toolcall.model;

/**
 * 工具执行结果
 */
public record ToolResult(
    String id,
    String name,
    Object result,
    String error,
    long durationMs
) {
    public boolean isSuccess() { return error == null; }
    
    public static ToolResult success(String id, String name, Object result, long durationMs) {
        return new ToolResult(id, name, result, null, durationMs);
    }
    
    public static ToolResult error(String id, String name, String error, long durationMs) {
        return new ToolResult(id, name, null, error, durationMs);
    }
}
