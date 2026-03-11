package com.toolcall;

import com.toolcall.model.ToolCall;
import com.toolcall.model.ToolResult;
import com.toolcall.parser.ToolCallParser.ParseResult;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolCall Framework 完整测试套件
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ToolCallTest {
    
    private static ToolCallFramework framework;
    
    @BeforeAll
    static void setup() {
        framework = new ToolCallFramework();
        framework.register(new DemoTools());
    }
    
    // ==================== 1. 注册测试 ====================
    
    @Test
    @Order(1)
    void testRegister() {
        assertTrue(framework.registry().has("get_weather"));
        assertTrue(framework.registry().has("search_hotels"));
    }
    
    // ==================== 2. Prompt 模式测试 ====================
    
    @Test
    @Order(2)
    void testSystemPrompt_ContainsXMLTags() {
        String prompt = framework.systemPrompt();
        
        assertNotNull(prompt);
        // 验证包含 XML 标签格式
        assertTrue(prompt.contains("<tool_calls>"));
        assertTrue(prompt.contains("</tool_calls>"));
        assertTrue(prompt.contains("<thinking>"));
        assertTrue(prompt.contains("</thinking>"));
    }
    
    @Test
    @Order(3)
    void testSystemPrompt_ContainsRules() {
        String prompt = framework.systemPrompt();
        
        assertTrue(prompt.contains("Output Rules"));
        assertTrue(prompt.contains("arguments MUST be a JSON string"));
    }
    
    @Test
    @Order(4)
    void testSystemPrompt_ContainsExamples() {
        String prompt = framework.systemPrompt();
        
        assertTrue(prompt.contains("Examples"));
        assertTrue(prompt.contains("Direct Answer"));
        assertTrue(prompt.contains("Single Tool Call"));
    }
    
    // ==================== 3. 解析器测试 ====================
    
    @Test
    @Order(5)
    void testParse_XMLFormat() {
        // 模拟 LLM 返回的 XML 格式
        String response = """
            <thinking>User wants weather info for Beijing</thinking>
            <tool_calls>
            [
              {
                "id": "call_001",
                "type": "function",
                "function": {
                  "name": "get_weather",
                  "arguments": "{\\"city\\": \\"Beijing\\", \\"unit\\": \\"celsius\\"}"
                }
              }
            ]
            </tool_calls>
            """;
        
        ParseResult result = framework.parse(response);
        
        assertTrue(result.textContent().isEmpty() || !result.textContent().contains("<tool_calls>"));
        assertEquals(1, result.toolCalls().size());
        assertEquals("call_001", result.toolCalls().get(0).id());
        assertEquals("get_weather", result.toolCalls().get(0).name());
        assertEquals("Beijing", result.toolCalls().get(0).arguments().get("city"));
    }
    
    @Test
    @Order(6)
    void testParse_WithThinking() {
        String response = """
            <thinking>分析用户需求...</thinking>
            <tool_calls>
            [{"id": "call_001", "name": "get_weather", "arguments": {"city": "Beijing"}}]
            </tool_calls>
            """;
        
        ParseResult result = framework.parse(response);
        
        assertNotNull(result.thinking());
        assertTrue(result.thinking().contains("分析用户需求"));
    }
    
    @Test
    @Order(7)
    void testParse_CleanText() {
        String response = """
            <thinking>思考过程</thinking>
            <tool_calls>
            [{"id": "call_001", "name": "get_weather", "arguments": {"city": "Beijing"}}]
            </tool_calls>
            Some additional text.
            """;
        
        ParseResult result = framework.parse(response);
        
        assertFalse(result.textContent().contains("<thinking>"));
        assertFalse(result.textContent().contains("<tool_calls>"));
    }
    
    @Test
    @Order(8)
    void testParse_JsonFormat() {
        // 也支持直接 JSON 格式
        String response = """
            {
              "tool_calls": [
                {"id": "call_001", "name": "get_weather", "arguments": {"city": "Beijing"}}
              ]
            }
            """;
        
        ParseResult result = framework.parse(response);
        
        assertEquals(1, result.toolCalls().size());
    }
    
    @Test
    @Order(9)
    void testParse_NoToolCalls() {
        String response = "Hello, how can I help you?";
        
        ParseResult result = framework.parse(response);
        
        assertTrue(result.toolCalls().isEmpty());
        assertEquals("Hello, how can I help you?", result.textContent());
    }
    
    // ==================== 4. 执行测试 ====================
    
    @Test
    @Order(10)
    void testExecute() {
        ToolCall call = new ToolCall("call_001", "get_weather", 
            Map.of("city", "Beijing", "unit", "celsius"));
        
        List<ToolResult> results = framework.execute(List.of(call));
        
        assertEquals(1, results.size());
        assertTrue(results.get(0).isSuccess());
    }
    
    // ==================== 5. 端到端测试 ====================
    
    @Test
    @Order(11)
    void testEndToEnd() {
        // 模拟完整流程
        // 1. 生成 prompt
        String prompt = framework.systemPrompt();
        assertNotNull(prompt);
        
        // 2. 模拟 LLM 返回
        String llmResponse = """
            <tool_calls>
            [{"id": "call_001", "name": "get_weather", "arguments": {"city": "Beijing"}}]
            </tool_calls>
            """;
        
        // 3. 解析
        ParseResult parsed = framework.parse(llmResponse);
        assertTrue(parsed.hasToolCalls());
        
        // 4. 执行
        List<ToolResult> results = framework.execute(parsed.toolCalls());
        assertEquals(1, results.size());
        assertTrue(results.get(0).isSuccess());
    }
}
