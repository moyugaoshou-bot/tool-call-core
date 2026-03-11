package com.toolcall;

import com.toolcall.model.ToolCall;
import com.toolcall.model.ToolResult;
import com.toolcall.parser.ToolCallParser.ParseResult;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端到端集成测试
 * 覆盖：工具注册 → 提示词生成 → LLM返回解析 → 工具执行
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class E2ETest {
    
    private static ToolCallFramework framework;
    
    @BeforeAll
    static void setup() {
        framework = new ToolCallFramework();
        framework.register(new DemoTools());
    }
    
    // ==================== 步骤1: 工具注册 ====================
    
    @Test
    @Order(1)
    void step1_registerTools() {
        // 验证工具已注册
        assertTrue(framework.registry().has("get_weather"));
        assertTrue(framework.registry().has("search_hotels"));
        assertTrue(framework.registry().has("book_hotel"));
        assertTrue(framework.registry().has("add_numbers"));
        assertEquals(6, framework.registry().getNames().size());
    }
    
    // ==================== 步骤2: 提示词生成 ====================
    
    @Test
    @Order(2)
    void step2_generatePrompt() {
        // 验证提示词包含必要内容
        String prompt = framework.systemPrompt();
        
        // 工具定义
        assertTrue(prompt.contains("get_weather"));
        assertTrue(prompt.contains("search_hotels"));
        
        // 参数定义
        assertTrue(prompt.contains("city"));
        assertTrue(prompt.contains("required"));
        
        // 输出格式规则
        assertTrue(prompt.contains("<tool_calls>"));
        assertTrue(prompt.contains("arguments"));
        
        // Few-shot 示例
        assertTrue(prompt.contains("Examples"));
    }
    
    // ==================== 步骤3: 模拟LLM返回 ====================
    
    // 测试各种 LLM 返回格式
    
    @Test
    @Order(3)
    void step3_parseLLMResponse_xmlStringArgs() {
        // 模拟 LLM 返回: XML标签 + string arguments
        String llmResponse = 
            "<tool_calls>" +
            "[{\"id\":\"call_001\",\"type\":\"function\",\"function\":{\"name\":\"get_weather\",\"arguments\":\"{\\\"city\\\":\\\"Beijing\\\"}\"}}]" +
            "</tool_calls>";
        
        ParseResult result = framework.parse(llmResponse);
        
        // 验证解析结果
        assertEquals(1, result.toolCalls().size());
        
        ToolCall call = result.toolCalls().get(0);
        assertEquals("call_001", call.id());
        assertEquals("get_weather", call.name());
        assertEquals("Beijing", call.arguments().get("city"));
    }
    
    @Test
    @Order(4)
    void step3_parseLLMResponse_jsonObjectArgs() {
        // 模拟 LLM 返回: JSON + object arguments
        String llmResponse = """
            {
              "tool_calls": [
                {
                  "id": "call_002",
                  "type": "function",
                  "function": {
                    "name": "add_numbers",
                    "arguments": {"a": 100, "b": 200}
                  }
                }
              ]
            }
            """;
        
        ParseResult result = framework.parse(llmResponse);
        
        assertEquals(1, result.toolCalls().size());
        ToolCall call = result.toolCalls().get(0);
        assertEquals("call_002", call.id());
        assertEquals("add_numbers", call.name());
        assertEquals(100, call.arguments().get("a"));
        assertEquals(200, call.arguments().get("b"));
    }
    
    @Test
    @Order(5)
    void step3_parseLLMResponse_multipleCalls() {
        // 模拟 LLM 返回: 多工具并行调用
        String llmResponse = """
            {
              "tool_calls": [
                {"id": "call_001", "name": "get_weather", "arguments": {"city": "Beijing"}},
                {"id": "call_002", "name": "search_hotels", "arguments": {"city": "Shanghai"}}
              ]
            }
            """;
        
        ParseResult result = framework.parse(llmResponse);
        
        assertEquals(2, result.toolCalls().size());
        assertEquals("get_weather", result.toolCalls().get(0).name());
        assertEquals("search_hotels", result.toolCalls().get(1).name());
    }
    
    @Test
    @Order(6)
    void step3_parseLLMResponse_noToolCall() {
        // 模拟 LLM 返回: 无工具调用（直接回答）
        String llmResponse = "Hello! How can I help you today?";
        
        ParseResult result = framework.parse(llmResponse);
        
        assertTrue(result.toolCalls().isEmpty());
        assertEquals("Hello! How can I help you today?", result.textContent());
    }
    
    // ==================== 步骤4: 工具执行 ====================
    
    @Test
    @Order(7)
    void step4_executeTool_stringReturn() {
        // 执行返回字符串的工具
        ToolCall call = new ToolCall("call_001", "get_weather", 
            Map.of("city", "Beijing", "unit", "celsius"));
        
        List<ToolResult> results = framework.execute(List.of(call));
        
        assertEquals(1, results.size());
        ToolResult result = results.get(0);
        
        assertTrue(result.isSuccess());
        assertEquals("call_001", result.id());
        assertEquals("get_weather", result.name());
        assertNotNull(result.result());
        assertTrue(result.result().toString().contains("天气"));
    }
    
    @Test
    @Order(8)
    void step4_executeTool_integerReturn() {
        // 执行返回整数的工具
        ToolCall call = new ToolCall("call_002", "add_numbers", 
            Map.of("a", 25, "b", 75));
        
        List<ToolResult> results = framework.execute(List.of(call));
        
        assertTrue(results.get(0).isSuccess());
        assertEquals(100, results.get(0).result());
    }
    
    @Test
    @Order(9)
    void step4_executeTool_listReturn() {
        // 执行返回列表的工具
        ToolCall call = new ToolCall("call_003", "search_hotels", 
            Map.of("city", "Beijing", "min_price", 300));
        
        List<ToolResult> results = framework.execute(List.of(call));
        
        assertTrue(results.get(0).isSuccess());
        assertNotNull(results.get(0).result());
    }
    
    @Test
    @Order(10)
    void step4_executeTool_withDefaults() {
        // 执行使用默认参数的工具
        ToolCall call = new ToolCall("call_004", "get_weather", 
            Map.of("city", "Shanghai"));  // unit 使用默认值
        
        List<ToolResult> results = framework.execute(List.of(call));
        
        assertTrue(results.get(0).isSuccess());
    }
    
    @Test
    @Order(11)
    void step4_executeTool_unknownTool() {
        // 执行未知工具
        ToolCall call = new ToolCall("call_005", "unknown_tool", Map.of());
        
        List<ToolResult> results = framework.execute(List.of(call));
        
        assertFalse(results.get(0).isSuccess());
        assertNotNull(results.get(0).error());
    }
    
    // ==================== 步骤5: 完整流程 ====================
    
    @Test
    @Order(12)
    void step5_fullWorkflow() {
        // 完整流程测试
        // 1. 注册工具 (已在上一步完成)
        
        // 2. 生成提示词
        String prompt = framework.systemPrompt();
        assertNotNull(prompt);
        
        // 3. 模拟 LLM 返回
        String llmResponse = 
            "<tool_calls>" +
            "[{\"id\":\"call_1\",\"name\":\"add_numbers\",\"arguments\":\"{\\\"a\\\":50,\\\"b\\\":50}\"}]" +
            "</tool_calls>";
        
        // 4. 解析
        ParseResult parsed = framework.parse(llmResponse);
        assertTrue(parsed.hasToolCalls());
        
        // 5. 执行
        List<ToolResult> execResults = framework.execute(parsed.toolCalls());
        
        // 6. 验证
        assertEquals(1, execResults.size());
        assertTrue(execResults.get(0).isSuccess());
        assertEquals(100, execResults.get(0).result());
    }
    
    // ==================== 边界测试 ====================
    
    @Test
    @Order(13)
    void boundary_emptyArguments() {
        // 无参数工具调用
        ToolCall call = new ToolCall("call_001", "add_numbers", Map.of());
        
        List<ToolResult> results = framework.execute(List.of(call));
        
        // 参数为0应该可以执行（int默认值为0）
        assertEquals(1, results.size());
    }
    
    @Test
    @Order(14)
    void boundary_malformedJson() {
        // 格式错误的 JSON
        String response = "{ invalid json }";
        
        ParseResult result = framework.parse(response);
        
        // 应该返回空列表，不抛出异常
        assertTrue(result.toolCalls().isEmpty());
    }
}
