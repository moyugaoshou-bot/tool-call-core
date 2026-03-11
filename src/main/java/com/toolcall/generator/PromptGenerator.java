package com.toolcall.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toolcall.model.FunctionDef;
import com.toolcall.registry.FunctionRegistry;

import java.util.*;

/**
 * Prompt Generator - OpenAI Tool-Call Prompt Format
 */
public class PromptGenerator {
    
    private final FunctionRegistry registry;
    private final ObjectMapper mapper;
    
    public PromptGenerator(FunctionRegistry registry) {
        this.registry = registry;
        this.mapper = new ObjectMapper();
    }
    
    public String toSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Tools\n\n## Available Tools\n\n");
        
        for (FunctionDef f : registry.getAllFunctions()) {
            sb.append("### ").append(f.name()).append("\n\n");
            sb.append("**Description**: ").append(f.description()).append("\n\n");
            sb.append("**Parameters**:\n```json\n");
            try {
                sb.append(mapper.writeValueAsString(f.toJsonSchema()));
            } catch (Exception e) {
                sb.append("{}");
            }
            sb.append("\n```\n\n");
        }
        
        sb.append(getOutputRules());
        sb.append(getFewShotExamples());
        
        return sb.toString();
    }
    
    public String getOutputRules() {
        StringBuilder sb = new StringBuilder();
        sb.append("## Output Rules\n\n");
        sb.append("Important: You must follow these format rules.\n\n");
        sb.append("- If you need to call a tool, output MUST be wrapped in <tool_calls> tags:\n");
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
        sb.append("- arguments MUST be a JSON string (escaped), NOT an object\n");
        sb.append("- If no tool is needed, output directly without <tool_calls> tags\n\n");
        sb.append("- Before calling tools, you can think in <thinking> tags:\n");
        sb.append("<thinking>What information does the user need?</thinking>\n\n");
        return sb.toString();
    }
    
    public String getFewShotExamples() {
        StringBuilder sb = new StringBuilder();
        String exampleTool = registry.getNames().iterator().next();
        
        sb.append("## Examples\n\n");
        
        sb.append("### Example 1: Direct Answer\n");
        sb.append("User: Hello\n");
        sb.append("Assistant: <thinking>Greeting, no tool needed.</thinking>\n");
        sb.append("Hello! How can I help you today?\n\n");
        
        sb.append("### Example 2: Single Tool Call\n");
        sb.append("User: What's the weather in Beijing?\n");
        sb.append("Assistant: <thinking>User asks about weather.</thinking>\n");
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
        
        sb.append("### Example 3: Multiple Parallel Calls\n");
        sb.append("User: Beijing or Shanghai weather?\n");
        sb.append("<tool_calls>\n");
        sb.append("[\n");
        sb.append("  {\"id\": \"call_2\", \"type\": \"function\", \"function\": {\"name\": \"").append(exampleTool).append("\", \"arguments\": \"{\\\"city\\\": \\\"Beijing\\\"}\"}},\n");
        sb.append("  {\"id\": \"call_3\", \"type\": \"function\", \"function\": {\"name\": \"").append(exampleTool).append("\", \"arguments\": \"{\\\"city\\\": \\\"Shanghai\\\"}\"}}\n");
        sb.append("]\n");
        sb.append("</tool_calls>\n\n");
        
        sb.append("### Example 4: Tool Result Handling\n");
        sb.append("System: <tool_response>{\"tool_call_id\": \"call_1\", \"name\": \"").append(exampleTool).append("\", \"content\": \"sunny\"}</tool_response>\n");
        sb.append("Assistant: <thinking>Got the result.</thinking>\n");
        sb.append("The weather is sunny.\n");
        
        return sb.toString();
    }
    
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
    
    public String toFullSystemPrompt() {
        return toSystemPrompt();
    }
    
    public String getCallingInstructions() {
        return getOutputRules();
    }
    
    public String getResultHandlingInstructions() {
        return "## Handling Tool Results\n\nWhen you receive tool results, analyze them.\n";
    }
}
