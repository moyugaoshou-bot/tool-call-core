package com.toolcall;

import com.toolcall.executor.ToolExecutor;
import com.toolcall.generator.PromptGenerator;
import com.toolcall.model.ToolCall;
import com.toolcall.model.ToolResult;
import com.toolcall.parser.ToolCallParser;
import com.toolcall.registry.FunctionRegistry;

import java.util.List;

/**
 * ToolCall Framework Main Entry
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
    
    /** Register tools */
    public ToolCallFramework register(Object instance) {
        registry.register(instance);
        return this;
    }
    
    /** Get registry */
    public FunctionRegistry registry() { return registry; }
    
    /** Generate OpenAI format tools JSON */
    public String toolsJson() { return promptGen.toOpenAIToolsJson(); }
    
    /** Generate natural language tools description */
    public String toolsDescription() { return promptGen.toNaturalLanguage(); }
    
    /** Parse tool calls from LLM response */
    public List<ToolCall> parse(String response) { return parser.parse(response); }
    
    /** Execute tool calls */
    public List<ToolResult> execute(List<ToolCall> calls) { return executor.execute(calls); }
    
    /** One-shot: parse and execute */
    public List<ToolResult> execute(String response) { return execute(parse(response)); }
    
    /** Shutdown executor */
    public void shutdown() { executor.shutdown(); }
}
