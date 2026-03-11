package com.toolcall.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toolcall.model.FunctionDef;
import com.toolcall.registry.FunctionRegistry;

import java.util.*;

/**
 * Prompt Generator - 综合 OpenAI 规范
 * 
 * 支持两种模式：
 * 1. API 模式: 通过 LLM API 的 tools 参数传入（OpenAI 官方格式）
 * 2. Prompt 模式: 将工具信息注入 system prompt（XML 标签格式）
 */
public class PromptGenerator {
    
    private final FunctionRegistry registry;
    private final ObjectMapper mapper;
    
    public PromptGenerator(FunctionRegistry registry) {
        this.registry = registry;
        this.mapper = new ObjectMapper();
    }
    
    // ==================== 1. API 模式：OpenAI 官方格式 ====================
    
    /**
     * 生成 OpenAI API 格式的 tools JSON
     * 用于 chat.completions.create 的 tools 参数
     * 
     * {
     *   "tools": [
     *     {
     *       "type": "function",
     *       "function": {
     *         "name": "get_weather",
     *         "description": "...",
     *         "parameters": {...}
     *       }
     *     }
     *   ]
     * }
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
        func.put("parameters", f.toJsonSchema());
        return Map.of("type", "function", "function", func);
    }
    
    // ==================== 2. Prompt 模式：XML 标签格式 ====================
    
    /**
     * 生成完整的 System Prompt
     * 包含：工具定义 + JSON Schema + 输出规则 + Few-shot 示例
     */
    public String toSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        
        // 1. 工具定义（Markdown 格式）
        sb.append("# Tools\n\n## Available Tools\n\n");
        
        for (FunctionDef f : registry.getAllFunctions()) {
            sb.append("### ").append(f.name()).append("\n\n");
            sb.append("**Description**: ").append(f.description()).append("\n\n");
            
            // 参数定义
            sb.append("**Parameters**:\n");
            if (f.parameters().properties().isEmpty()) {
                sb.append("  - (none)\n");
            } else {
                for (Map.Entry<String, FunctionDef.ParamSchema> entry : f.parameters().properties().entrySet()) {
                    FunctionDef.ParamSchema p = entry.getValue();
                    boolean required = f.parameters().required().contains(entry.getKey());
                    sb.append("  - `").append(entry.getKey()).append("`");
                    if (required) sb.append(" (required)");
                    sb.append(": ").append(p.type());
                    if (p.description() != null && !p.description().isEmpty()) {
                        sb.append(" - ").append(p.description());
                    }
                    sb.append("\n");
                }
            }
            sb.append("\n");
        }
        
        // 2. 输出规则
        sb.append(getOutputRules());
        
        // 3. Few-shot 示例
        sb.append(getFewShotExamples());
        
        return sb.toString();
    }
    
    /**
     * 输出规则 - 告诉 LLM 如何返回工具调用
     */
    public String getOutputRules() {
        StringBuilder sb = new StringBuilder();
        sb.append("## Output Rules\n\n");
        sb.append("When you need to call a tool, you MUST output in the following format:\n\n");
        
        // XML 标签格式
        sb.append("<tool_calls>\n");
        sb.append("[\n");
        sb.append("  {\n");
        sb.append("    \"id\": \"call_001\",\n");
        sb.append("    \"type\": \"function\",\n");
        sb.append("    \"function\": {\n");
        sb.append("      \"name\": \"tool_name\",\n");
        sb.append("      \"arguments\": \"{\\\"param\\\": \\\"value\\\"}\"\n");
        sb.append("    }\n");
        sb.append("  }\n");
        sb.append("]\n");
        sb.append("</tool_calls>\n\n");
        
        sb.append("Important:\n");
        sb.append("1. Always wrap tool calls in <tool_calls> tags\n");
        sb.append("2. arguments MUST be a JSON string (escaped), NOT an object\n");
        sb.append("3. Each call must have a unique id\n");
        sb.append("4. If no tool is needed, output directly without tags\n\n");
        
        // 可选的思考标签
        sb.append("You can think before acting:\n");
        sb.append("<thinking>Your reasoning here</thinking>\n\n");
        
        return sb.toString();
    }
    
    /**
     * Few-shot 示例
     */
    public String getFewShotExamples() {
        StringBuilder sb = new StringBuilder();
        String exampleTool = registry.getNames().iterator().next();
        
        sb.append("## Examples\n\n");
        
        // 示例 1: 直接回答
        sb.append("### Direct Answer\n");
        sb.append("User: Hello\n");
        sb.append("Assistant: Hello! How can I help you today?\n\n");
        
        // 示例 2: 单工具调用
        sb.append("### Single Tool Call\n");
        sb.append("User: What's the weather in Beijing?\n");
        sb.append("Assistant: <thinking>Need to get weather for Beijing</thinking>\n");
        sb.append("<tool_calls>\n");
        sb.append("[\n");
        sb.append("  {\n");
        sb.append("    \"id\": \"call_1\",\n");
        sb.append("    \"type\": \"function\",\n");
        sb.append("    \"function\": {\n");
        sb.append("      \"name\": \"").append(exampleTool).append("\",\n");
        sb.append("      \"arguments\": \"{\\\"city\\\": \\\"Beijing\\\"}\"\n");
        sb.append("    }\n");
        sb.append("  }\n");
        sb.append("]\n");
        sb.append("</tool_calls>\n\n");
        
        // 示例 3: 多工具并行
        sb.append("### Multiple Parallel Calls\n");
        sb.append("User: Compare weather in Beijing and Shanghai\n");
        sb.append("Assistant: <thinking>Need weather for both cities</thinking>\n");
        sb.append("<tool_calls>\n");
        sb.append("[\n");
        sb.append("  {\"id\": \"call_2\", \"type\": \"function\", \"function\": {\"name\": \"").append(exampleTool).append("\", \"arguments\": \"{\\\"city\\\": \\\"Beijing\\\"}\"}},\n");
        sb.append("  {\"id\": \"call_3\", \"type\": \"function\", \"function\": {\"name\": \"").append(exampleTool).append("\", \"arguments\": \"{\\\"city\\\": \\\"Shanghai\\\"}\"}}\n");
        sb.append("]\n");
        sb.append("</tool_calls>\n\n");
        
        // 示例 4: 工具结果处理
        sb.append("### Tool Result Handling\n");
        sb.append("User: What's the weather?\n");
        sb.append("Assistant: <tool_calls>\n");
        sb.append("[\n");
        sb.append("  {\"id\": \"call_1\", \"type\": \"function\", \"function\": {\"name\": \"").append(exampleTool).append("\", \"arguments\": \"{}\"}}\n");
        sb.append("]\n");
        sb.append("</tool_calls>\n\n");
        
        sb.append("System: <tool_response>{\"tool_call_id\": \"call_1\", \"content\": \"sunny, 25C\"}</tool_response>\n");
        sb.append("Assistant: The weather is sunny, 25C.\n");
        
        return sb.toString();
    }
    
    // ==================== 便捷方法 ====================
    
    /** 完整 System Prompt */
    public String toFullSystemPrompt() {
        return toSystemPrompt();
    }
    
    /** 工具调用格式说明 */
    public String getCallingInstructions() {
        return getOutputRules();
    }
    
    /** 工具结果处理说明 */
    public String getResultHandlingInstructions() {
        return "## Tool Results\n\nWhen you receive <tool_response> tags, analyze them and provide your answer.\n";
    }
}
