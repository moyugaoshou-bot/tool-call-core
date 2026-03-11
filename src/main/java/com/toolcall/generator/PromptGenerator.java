package com.toolcall.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toolcall.model.FunctionDef;
import com.toolcall.registry.FunctionRegistry;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 提示词生成器 - 将工具信息注入到 system prompt
 * 
 * 支持两种模式：
 * 1. API 模式: 通过 LLM API 的 tools 参数传入（JSON格式）
 * 2. Prompt 模式: 将工具信息写入 system prompt，让 LLM 自行决定调用
 */
public class PromptGenerator {
    
    private final FunctionRegistry registry;
    private final ObjectMapper mapper;
    
    public PromptGenerator(FunctionRegistry registry) {
        this.registry = registry;
        this.mapper = new ObjectMapper();
    }
    
    // ==================== API 模式：生成 OpenAI 格式 tools JSON ====================
    
    /**
     * 生成 OpenAI API 格式的 tools JSON
     * 用于 LLM API 的 tools 参数
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
    
    // ==================== Prompt 模式：生成提示词 ====================
    
    /**
     * 生成完整的 system prompt（包含工具信息）
     * 这是给 LLM 看的提示词，告诉它有哪些工具可用
     */
    public String toSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("You have access to the following functions:\n\n");
        
        for (FunctionDef f : registry.getAllFunctions()) {
            sb.append("## ").append(f.name()).append("\n");
            sb.append(f.description()).append("\n");
            sb.append("Parameters:\n");
            
            if (f.parameters().properties().isEmpty()) {
                sb.append("  - (none)\n");
            } else {
                for (var entry : f.parameters().properties().entrySet()) {
                    var p = entry.getValue();
                    sb.append("  - ").append(entry.getKey());
                    if (f.parameters().required().contains(entry.getKey())) {
                        sb.append(" (required)");
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
        
        sb.append("\n").append(getCallingInstructions());
        
        return sb.toString();
    }
    
    /**
     * 生成工具调用说明 - 告诉 LLM 如何返回工具调用
     */
    public String getCallingInstructions() {
        return """
## How to Call Functions

When you need to use a function, respond with a JSON object in the following format:

{
  "tool_calls": [
    {
      "id": "call_001",
      "name": "function_name",
      "arguments": {
        "param1": "value1",
        "param2": "value2"
      }
    }
  ]
}

Important:
- Always use "tool_calls" key for function calls
- Each call must have a unique "id"
- Use "arguments" for parameter values
- Do not call functions in your text response, only in the JSON block
""";
    }
    
    /**
     * 生成工具结果处理说明
     */
    public String getResultHandlingInstructions() {
        return """
## Handling Tool Results

When you receive tool results, analyze them and provide your final answer to the user.
The results will be in this format:

{
  "tool_results": [
    {
      "id": "call_001",
      "name": "function_name",
      "result": "the result value",
      "success": true
    }
  ]
}
""";
    }
    
    /**
     * 生成完整的 system prompt（包含工具信息 + 调用说明 + 结果处理）
     */
    public String toFullSystemPrompt() {
        return toSystemPrompt() + "\n" + getResultHandlingInstructions();
    }
}
