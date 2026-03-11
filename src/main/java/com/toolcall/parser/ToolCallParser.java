package com.toolcall.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toolcall.model.ToolCall;

import java.util.*;

/**
 * 解析 LLM 返回的 JSON 工具调用
 * 
 * 支持 OpenAI Function Calling 返回格式:
 * {
 *   "tool_calls": [
 *     {
 *       "id": "call_abc123",
 *       "type": "function",
 *       "function": {
 *         "name": "get_weather",
 *         "arguments": "{\"city\": \"Beijing\"}"
 *       }
 *     }
 *   ]
 * }
 */
public class ToolCallParser {
    
    private final ObjectMapper mapper;
    
    public ToolCallParser() {
        this.mapper = new ObjectMapper();
    }
    
    /**
     * 解析 LLM 响应，提取工具调用
     * 支持多种格式：
     * 1. OpenAI 格式: { "tool_calls": [...] }
     * 2. 纯数组格式: [...]
     */
    public List<ToolCall> parse(String response) {
        List<ToolCall> calls = new ArrayList<>();
        if (response == null || response.isBlank()) return calls;
        
        try {
            JsonNode root = mapper.readTree(response);
            
            // 1. 尝试 OpenAI 格式: tool_calls[]
            JsonNode toolCalls = root.path("tool_calls");
            if (toolCalls.isArray()) {
                for (JsonNode node : toolCalls) {
                    ToolCall call = parseOpenAIFormat(node);
                    if (call != null) calls.add(call);
                }
            }
            
            // 2. 支持纯数组格式
            if (calls.isEmpty() && root.isArray()) {
                for (JsonNode node : root) {
                    ToolCall call = parseOpenAIFormat(node);
                    if (call != null) calls.add(call);
                }
            }
            
        } catch (Exception e) {
            // 不是 JSON 格式，返回空列表
        }
        
        return calls;
    }
    
    /**
     * 解析 OpenAI 格式的工具调用
     * 支持两种变体：
     * 1. 完整格式: { "id": "...", "type": "function", "function": { "name": "...", "arguments": "..." } }
     * 2. 简化格式: { "id": "...", "name": "...", "arguments": {...} }
     */
    private ToolCall parseOpenAIFormat(JsonNode node) {
        try {
            // 提取 id
            String id = node.has("id") ? node.get("id").asText() 
                : "call_" + UUID.randomUUID().toString().substring(0, 8);
            
            // 提取函数名 - 支持两种格式
            String name = null;
            
            // 格式1: function.name
            JsonNode functionNode = node.path("function");
            if (functionNode.isObject()) {
                name = functionNode.path("name").asText();
            }
            
            // 格式2: 直接在根节点
            if (name == null || name.isBlank()) {
                name = node.path("name").asText();
            }
            
            if (name == null || name.isBlank()) return null;
            
            // 提取参数 - 支持多种格式
            Map<String, Object> args = new HashMap<>();
            
            // 尝试从 function.arguments 获取
            JsonNode argsNode = null;
            if (functionNode.isObject()) {
                argsNode = functionNode.path("arguments");
            }
            
            // 尝试从根节点 arguments 获取
            if (argsNode == null || argsNode.isMissingNode()) {
                argsNode = node.path("arguments");
            }
            
            if (argsNode != null && !argsNode.isMissingNode()) {
                if (argsNode.isObject()) {
                    // arguments 是对象: { "city": "Beijing" }
                    args = mapper.convertValue(argsNode, Map.class);
                } else if (argsNode.isTextual()) {
                    // arguments 是字符串: "{\"city\": \"Beijing\"}"
                    String argsStr = argsNode.asText();
                    if (argsStr.startsWith("{") && argsStr.endsWith("}")) {
                        args = mapper.readValue(argsStr, Map.class);
                    }
                }
            }
            
            return new ToolCall(id, name, args);
            
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 检查响应是否包含工具调用
     */
    public boolean hasToolCalls(String response) {
        return !parse(response).isEmpty();
    }
}
