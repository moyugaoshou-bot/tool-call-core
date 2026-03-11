package com.toolcall.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toolcall.model.FunctionDef;
import com.toolcall.registry.FunctionRegistry;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 提示词生成器 - 生成 LLM 可理解的工具描述
 */
public class PromptGenerator {
    
    private final FunctionRegistry registry;
    private final ObjectMapper mapper;
    
    public PromptGenerator(FunctionRegistry registry) {
        this.registry = registry;
        this.mapper = new ObjectMapper();
    }
    
    /**
     * 生成 OpenAI 格式的 tools JSON
     */
    public String toOpenAIToolsJson() {
        try {
            List<Map<String, Object>> tools = registry.getAllFunctions().stream()
                .map(this::toOpenAIFunction)
                .toList();
            return mapper.writeValueAsString(Map.of("tools", tools));
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate tools JSON", e);
        }
    }
    
    private Map<String, Object> toOpenAIFunction(FunctionDef f) {
        Map<String, Object> func = new LinkedHashMap<>();
        func.put("name", f.name());
        func.put("description", f.description());
        
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", f.parameters().properties());
        schema.put("required", f.parameters().required());
        func.put("parameters", schema);
        
        return Map.of("type", "function", "function", func);
    }
    
    /**
     * 生成自然语言工具描述（用于 system prompt）
     */
    public String toNaturalLanguage() {
        StringBuilder sb = new StringBuilder();
        sb.append("## Available Functions/Tools\n\n");
        sb.append("You can call these functions to help answer the user's question:\n\n");
        
        for (FunctionDef f : registry.getAllFunctions()) {
            sb.append("### ").append(f.name()).append("\n");
            sb.append(f.description()).append("\n");
            sb.append("**Parameters:**\n");
            
            if (f.parameters().properties().isEmpty()) {
                sb.append("  - (none)\n");
            } else {
                for (var entry : f.parameters().properties().entrySet()) {
                    var p = entry.getValue();
                    sb.append("  - `").append(entry.getKey()).append("`");
                    if (f.parameters().required().contains(entry.getKey())) {
                        sb.append(" [required]");
                    }
                    sb.append(": ").append(p.type());
                    if (p.description() != null && !p.description().isEmpty()) {
                        sb.append(" - ").append(p.description());
                    }
                    sb.append("\n");
                }
            }
            sb.append("\n");
        }
        
        sb.append("\n## How to Call Functions\n\n");
        sb.append("When you need to call a function, respond with a JSON object:\n\n");
        sb.append("```json\n");
        sb.append("{\n");
        sb.append("  \"tool_calls\": [\n");
        sb.append("    {\n");
        sb.append("      \"id\": \"call_001\",\n");
        sb.append("      \"name\": \"function_name\",\n");
        sb.append("      \"arguments\": {\n");
        sb.append("        \"param1\": \"value1\",\n");
        sb.append("        \"param2\": \"value2\"\n");
        sb.append("      }\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n");
        sb.append("```\n\n");
        sb.append("After receiving tool results, continue with your answer.\n");
        
        return sb.toString();
    }
    
    /**
     * 生成工具结果格式化说明
     */
    public String getResultFormatInstructions() {
        return """
            ## Tool Result Format
            
            When a tool is executed, you'll receive results in this format:
            
            ```json
            {
              "tool_results": [
                {
                  "id": "call_001",
                  "name": "function_name",
                  "result": "the result value or error message",
                  "success": true
                }
              ]
            }
            ```
            
            Use these results to formulate your final answer to the user.
            """;
    }
}
