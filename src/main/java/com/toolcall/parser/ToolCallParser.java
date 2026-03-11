package com.toolcall.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toolcall.model.ToolCall;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parse LLM response, extract tool calls
 */
public class ToolCallParser {
    
    private final ObjectMapper mapper;
    
    private static final Pattern TOOL_CALLS_PATTERN = Pattern.compile(
        "<tool_calls>(.*?)</tool_calls>", 
        Pattern.DOTALL
    );
    
    private static final Pattern THINKING_PATTERN = Pattern.compile(
        "<thinking>(.*?)</thinking>",
        Pattern.DOTALL
    );
    
    public ToolCallParser() {
        this.mapper = new ObjectMapper();
    }
    
    public ParseResult parse(String response) {
        if (response == null || response.isBlank()) {
            return new ParseResult("", List.of(), null);
        }
        
        String thinking = extractThinking(response);
        List<ToolCall> toolCalls = extractToolCalls(response);
        String cleanText = cleanText(response);
        
        return new ParseResult(cleanText, toolCalls, thinking);
    }
    
    private String extractThinking(String response) {
        Matcher m = THINKING_PATTERN.matcher(response);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }
    
    private List<ToolCall> extractToolCalls(String response) {
        List<ToolCall> calls = new ArrayList<>();
        
        // Try <tool_calls>...</tool_calls>
        Matcher m = TOOL_CALLS_PATTERN.matcher(response);
        if (m.find()) {
            String jsonContent = m.group(1).trim();
            try {
                JsonNode root = mapper.readTree(jsonContent);
                if (root.isArray()) {
                    for (JsonNode node : root) {
                        ToolCall call = parseToolCall(node);
                        if (call != null) calls.add(call);
                    }
                    return calls;
                }
            } catch (Exception e) {
                // ignore
            }
        }
        
        // Try direct JSON format
        try {
            JsonNode root = mapper.readTree(response);
            JsonNode toolCalls = root.path("tool_calls");
            if (toolCalls.isArray()) {
                for (JsonNode node : toolCalls) {
                    ToolCall call = parseToolCall(node);
                    if (call != null) calls.add(call);
                }
            }
        } catch (Exception e) {
            // ignore
        }
        
        return calls;
    }
    
    private ToolCall parseToolCall(JsonNode node) {
        try {
            String id = node.has("id") ? node.get("id").asText() 
                : "call_" + UUID.randomUUID().toString().substring(0, 8);
            
            String name = null;
            JsonNode functionNode = node.path("function");
            if (functionNode.isObject()) {
                name = functionNode.path("name").asText();
            }
            if (name == null || name.isBlank()) {
                name = node.path("name").asText();
            }
            
            if (name == null || name.isBlank()) return null;
            
            Map<String, Object> args = new HashMap<>();
            JsonNode argsNode = functionNode.isObject() ? functionNode.path("arguments") : null;
            if (argsNode == null || argsNode.isMissingNode()) {
                argsNode = node.path("arguments");
            }
            
            if (argsNode != null && !argsNode.isMissingNode()) {
                if (argsNode.isObject()) {
                    args = mapper.convertValue(argsNode, Map.class);
                } else if (argsNode.isTextual()) {
                    String argsStr = argsNode.asText();
                    if (argsStr.startsWith("{")) {
                        args = mapper.readValue(argsStr, Map.class);
                    }
                }
            }
            
            return new ToolCall(id, name, args);
            
        } catch (Exception e) {
            return null;
        }
    }
    
    private String cleanText(String response) {
        // Remove thinking
        String cleaned = THINKING_PATTERN.matcher(response).replaceAll("");
        // Remove tool_calls
        cleaned = TOOL_CALLS_PATTERN.matcher(cleaned).replaceAll("");
        // Remove tool_response
        cleaned = cleaned.replaceAll("<tool_response>.*?</tool_response>", "");
        
        return cleaned.trim();
    }
    
    public boolean hasToolCalls(String response) {
        return !parse(response).toolCalls().isEmpty();
    }
    
    public record ParseResult(
        String textContent,
        List<ToolCall> toolCalls,
        String thinking
    ) {
        public boolean hasToolCalls() {
            return !toolCalls.isEmpty();
        }
    }
}
