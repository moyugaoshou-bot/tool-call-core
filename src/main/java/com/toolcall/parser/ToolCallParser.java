package com.toolcall.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toolcall.model.ToolCall;

import java.util.*;

/**
 * 解析 LLM 返回的 JSON 工具调用
 */
public class ToolCallParser {
    
    private final ObjectMapper mapper = new ObjectMapper();
    
    /**
     * 解析 LLM 响应，提取工具调用
     */
    public List<ToolCall> parse(String response) {
        List<ToolCall> calls = new ArrayList<>();
        if (response == null || response.isBlank()) return calls;
        
        try {
            JsonNode root = mapper.readTree(response);
            
            // 支持多种格式
            JsonNode toolCalls = root.path("tool_calls");
            if (toolCalls.isArray()) {
                for (JsonNode node : toolCalls) {
                    ToolCall call = parseCall(node);
                    if (call != null) calls.add(call);
                }
            }
            
            // 支持纯数组格式
            if (calls.isEmpty() && root.isArray()) {
                for (JsonNode node : root) {
                    ToolCall call = parseCall(node);
                    if (call != null) calls.add(call);
                }
            }
            
        } catch (Exception e) {
            // 不是 JSON 格式，返回空
        }
        
        return calls;
    }
    
    private ToolCall parseCall(JsonNode node) {
        try {
            String id = node.has("id") ? node.get("id").asText() 
                : "call_" + UUID.randomUUID().toString().substring(0, 8);
            
            String name = node.has("name") ? node.get("name").asText()
                : node.path("function").path("name").asText();
            
            if (name.isBlank()) return null;
            
            Map<String, Object> args = new HashMap<>();
            JsonNode argsNode = node.has("arguments") ? node.get("arguments")
                : node.path("function").path("arguments");
            
            if (argsNode.isObject()) {
                args = mapper.convertValue(argsNode, Map.class);
            } else if (argsNode.isTextual()) {
                String argsStr = argsNode.asText();
                if (argsStr.startsWith("{")) {
                    args = mapper.readValue(argsStr, Map.class);
                }
            }
            
            return new ToolCall(id, name, args);
            
        } catch (Exception e) {
            return null;
        }
    }
    
    public boolean hasToolCalls(String response) {
        return !parse(response).isEmpty();
    }
}
