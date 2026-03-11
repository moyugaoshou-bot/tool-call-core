package com.toolcall;

import com.toolcall.executor.ToolExecutor;
import com.toolcall.generator.PromptGenerator;
import com.toolcall.model.ToolCall;
import com.toolcall.model.ToolResult;
import com.toolcall.parser.ToolCallParser;
import com.toolcall.registry.FunctionRegistry;

import java.util.List;

/**
 * ToolCall Framework - 零依赖AI框架的纯Java实现
 * 
 * 支持两种模式：
 * 1. API 模式: 通过 LLM API 的 tools 参数传入工具定义
 * 2. Prompt 模式: 将工具信息注入 system prompt，让 LLM 自行决定调用
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
    
    // ==================== API 模式 ====================
    
    /** 生成 OpenAI API 格式的 tools JSON（用于 API 的 tools 参数） */
    public String toolsJson() { return promptGen.toOpenAIToolsJson(); }
    
    // ==================== Prompt 模式 ====================
    
    /** 生成 system prompt（包含工具信息，用于注入到提示词） */
    public String systemPrompt() { return promptGen.toSystemPrompt(); }
    
    /** 生成完整的 system prompt（含工具+调用说明+结果处理） */
    public String fullSystemPrompt() { return promptGen.toFullSystemPrompt(); }
    
    /** 生成工具调用说明 */
    public String callingInstructions() { return promptGen.getCallingInstructions(); }
    
    /** 生成工具结果处理说明 */
    public String resultHandlingInstructions() { return promptGen.getResultHandlingInstructions(); }
    
    // ==================== 解析和执行 ====================
    
    /** 解析 LLM 返回的文本，提取工具调用 */
    public List<ToolCall> parse(String llmResponse) { return parser.parse(llmResponse); }
    
    /** 执行工具调用 */
    public List<ToolResult> execute(List<ToolCall> calls) { return executor.execute(calls); }
    
    /** 一键执行：从 LLM 响应解析并执行工具 */
    public List<ToolResult> execute(String llmResponse) { return execute(parse(llmResponse)); }
    
    /** 关闭执行器 */
    public void shutdown() { executor.shutdown(); }
}
