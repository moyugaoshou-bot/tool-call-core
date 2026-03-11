package com.toolcall;

import com.toolcall.executor.ToolExecutor;
import com.toolcall.generator.PromptGenerator;
import com.toolcall.model.ToolCall;
import com.toolcall.model.ToolResult;
import com.toolcall.parser.ToolCallParser;
import com.toolcall.parser.ToolCallParser.ParseResult;
import com.toolcall.registry.FunctionRegistry;

import java.util.List;

/**
 * ToolCall Framework - 零依赖AI框架的纯Java实现
 * 
 * 核心流程：
 * 1. 注册工具 (register)
 * 2. 生成 System Prompt (systemPrompt / fullSystemPrompt)
 * 3. 将 Prompt 注入 LLM
 * 4. 解析 LLM 响应 (parse)
 * 5. 执行工具 (execute)
 * 6. 将结果返回给 LLM 处理
 */
public class ToolCallFramework {
    
    private final FunctionRegistry registry;
    private final PromptGenerator promptGen;
    private final ToolCallParser parser;
    private final ToolExecutor executor;
    
    public ToolCallFramework() {
        this.registry = new FunctionRegistry();
        this.promptGen = new PromptGenerator(registry);
        this.parser = new ToolCallParser();
        this.executor = new ToolExecutor(registry);
    }
    
    public ToolCallFramework(int poolSize) {
        this.registry = new FunctionRegistry();
        this.promptGen = new PromptGenerator(registry);
        this.parser = new ToolCallParser();
        this.executor = new ToolExecutor(registry, poolSize);
    }
    
    // ==================== 工具注册 ====================
    
    /** 注册工具对象 */
    public ToolCallFramework register(Object instance) {
        registry.register(instance);
        return this;
    }
    
    /** 获取注册表 */
    public FunctionRegistry registry() { return registry; }
    
    // ==================== Prompt 模式（核心） ====================
    
    /** 生成完整 System Prompt（工具定义 + 规则 + 示例） */
    public String systemPrompt() { return promptGen.toSystemPrompt(); }
    
    /** 工具调用格式说明 */
    public String callingInstructions() { return promptGen.getOutputRules(); }
    
    /** Few-shot 示例 */
    public String fewShotExamples() { return promptGen.getFewShotExamples(); }
    
    // ==================== API 模式（可选） ====================
    
    /** 生成 OpenAI API 格式的 tools JSON */
    public String toolsJson() { return promptGen.toOpenAIToolsJson(); }
    
    // ==================== 解析 ====================
    
    /** 解析 LLM 响应，返回 ParseResult */
    public ParseResult parse(String llmResponse) { 
        return parser.parse(llmResponse); 
    }
    
    /** 解析并提取工具调用 */
    public List<ToolCall> parseToolCalls(String llmResponse) { 
        return parser.parse(llmResponse).toolCalls(); 
    }
    
    // ==================== 执行 ====================
    
    /** 执行工具调用 */
    public List<ToolResult> execute(List<ToolCall> calls) { 
        return executor.execute(calls); 
    }
    
    /** 一键执行：从解析到执行 */
    public List<ToolResult> execute(String llmResponse) { 
        return execute(parseToolCalls(llmResponse)); 
    }
    
    /** 关闭执行器 */
    public void shutdown() { executor.shutdown(); }
}
