package com.toolcall;

import com.toolcall.model.ToolCall;
import com.toolcall.model.ToolResult;
import com.toolcall.registry.FunctionRegistry;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolCall Framework 完整测试套件
 * 
 * 测试覆盖:
 * 1. 工具注册
 * 2. API 模式: OpenAI 格式工具定义生成
 * 3. Prompt 模式: System prompt 生成
 * 4. OpenAI 格式工具调用解析
 * 5. 工具执行
 * 6. 依赖执行
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ToolCallTest {
    
    private static ToolCallFramework framework;
    
    @BeforeAll
    static void setup() {
        framework = new ToolCallFramework();
        framework.register(new DemoTools());
    }
    
    // ==================== 1. 注册表测试 ====================
    
    @Test
    @Order(1)
    void testRegister() {
        assertTrue(framework.registry().has("get_weather"));
        assertTrue(framework.registry().has("search_hotels"));
        assertTrue(framework.registry().has("book_hotel"));
    }
    
    // ==================== 2. API 模式测试 ====================
    
    @Test
    @Order(2)
    void testToolsJson_OpenAIFormat() {
        String json = framework.toolsJson();
        assertNotNull(json);
        
        // 验证 OpenAI 格式
        assertTrue(json.contains("\"tools\""));
        assertTrue(json.contains("\"type\":\"function\""));
        assertTrue(json.contains("\"name\":\"get_weather\""));
    }
    
    // ==================== 3. Prompt 模式测试 ====================
    
    @Test
    @Order(3)
    void testSystemPrompt() {
        String prompt = framework.systemPrompt();
        
        assertNotNull(prompt);
        assertTrue(prompt.contains("You have access to the following functions"));
        assertTrue(prompt.contains("get_weather"));
        assertTrue(prompt.contains("search_hotels"));
        assertTrue(prompt.contains("city"));
    }
    
    @Test
    @Order(4)
    void testFullSystemPrompt() {
        String prompt = framework.fullSystemPrompt();
        
        assertNotNull(prompt);
        assertTrue(prompt.contains("You have access to the following functions"));
        assertTrue(prompt.contains("tool_calls"));  // 调用说明
        assertTrue(prompt.contains("tool_results")); // 结果处理说明
    }
    
    @Test
    @Order(5)
    void testCallingInstructions() {
        String instructions = framework.callingInstructions();
        
        assertNotNull(instructions);
        assertTrue(instructions.contains("tool_calls"));
        assertTrue(instructions.contains("\"id\":"));
        assertTrue(instructions.contains("\"name\":"));
        assertTrue(instructions.contains("\"arguments\":"));
    }
    
    @Test
    @Order(6)
    void testResultHandlingInstructions() {
        String instructions = framework.resultHandlingInstructions();
        
        assertNotNull(instructions);
        assertTrue(instructions.contains("tool_results"));
        assertTrue(instructions.contains("success"));
    }
    
    // ==================== 4. 解析器测试 ====================
    
    @Test
    @Order(7)
    void testParse_OpenAIFormat() {
        String response = """
            {
              "tool_calls": [
                {
                  "id": "call_abc123",
                  "type": "function",
                  "function": {
                    "name": "get_weather",
                    "arguments": {"city": "Beijing", "unit": "celsius"}
                  }
                }
              ]
            }
            """;
        
        List<ToolCall> calls = framework.parse(response);
        
        assertEquals(1, calls.size());
        assertEquals("call_abc123", calls.get(0).id());
        assertEquals("get_weather", calls.get(0).name());
        assertEquals("Beijing", calls.get(0).arguments().get("city"));
    }
    
    @Test
    @Order(8)
    void testParse_MultipleToolCalls() {
        String response = """
            {
              "tool_calls": [
                {"id": "call_001", "type": "function", "function": {"name": "get_weather", "arguments": {"city": "Beijing"}}},
                {"id": "call_002", "type": "function", "function": {"name": "search_hotels", "arguments": {"city": "Shanghai"}}}
              ]
            }
            """;
        
        List<ToolCall> calls = framework.parse(response);
        assertEquals(2, calls.size());
    }
    
    @Test
    @Order(9)
    void testParse_EmptyResponse() {
        assertTrue(framework.parse("").isEmpty());
        assertTrue(framework.parse(null).isEmpty());
        assertTrue(framework.parse("hello").isEmpty());
    }
    
    // ==================== 5. 执行器测试 ====================
    
    @Test
    @Order(10)
    void testExecuteSingle() {
        ToolCall call = new ToolCall("call_001", "get_weather", 
            Map.of("city", "Shanghai", "unit", "celsius"));
        
        List<ToolResult> results = framework.execute(List.of(call));
        
        assertEquals(1, results.size());
        assertTrue(results.get(0).isSuccess());
    }
    
    @Test
    @Order(11)
    void testExecuteMultiple() {
        ToolCall c1 = new ToolCall("call_001", "get_weather", Map.of("city", "Beijing"));
        ToolCall c2 = new ToolCall("call_002", "search_hotels", Map.of("city", "Shanghai"));
        
        List<ToolResult> results = framework.execute(List.of(c1, c2));
        
        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(ToolResult::isSuccess));
    }
    
    @Test
    @Order(12)
    void testExecuteIntegerReturn() {
        ToolCall call = new ToolCall("call_001", "add_numbers", Map.of("a", 10, "b", 20));
        
        List<ToolResult> results = framework.execute(List.of(call));
        
        assertTrue(results.get(0).isSuccess());
        assertEquals(30, results.get(0).result());
    }
    
    @Test
    @Order(13)
    void testExecuteUnknownTool() {
        ToolCall call = new ToolCall("call_001", "unknown_tool", Map.of());
        
        List<ToolResult> results = framework.execute(List.of(call));
        
        assertFalse(results.get(0).isSuccess());
    }
    
    // ==================== 6. One-shot 执行 ====================
    
    @Test
    @Order(14)
    void testExecuteOneShot() {
        String response = """
            {
              "tool_calls": [
                {"id": "call_001", "type": "function", "function": {"name": "add_numbers", "arguments": {"a": 100, "b": 200}}}
              ]
            }
            """;
        
        List<ToolResult> results = framework.execute(response);
        
        assertEquals(1, results.size());
        assertTrue(results.get(0).isSuccess());
        assertEquals(300, results.get(0).result());
    }
    
    // ==================== 7. 边界测试 ====================
    
    @Test
    @Order(15)
    void testExecuteEmptyList() {
        List<ToolResult> results = framework.execute(List.of());
        assertTrue(results.isEmpty());
    }
}
