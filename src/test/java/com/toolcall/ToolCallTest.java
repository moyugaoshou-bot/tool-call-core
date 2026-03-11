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
    
    // ==================== 2. API 模式测试 ====================
    
    @Test
    @Order(2)
    void testOpenAIToolsJson() {
        String json = framework.toolsJson();
        
        assertNotNull(json);
        // OpenAI 格式
        assertTrue(json.contains("\"tools\""));
        assertTrue(json.contains("\"type\":\"function\""));
        assertTrue(json.contains("\"name\":\"get_weather\""));
        assertTrue(json.contains("\"parameters\""));
        assertTrue(json.contains("\"required\":[\"city\"]"));
    }
    
    // ==================== 3. Prompt 模式测试 ====================
    
    @Test
    @Order(3)
    void testSystemPrompt() {
        String prompt = framework.systemPrompt();
        
        assertNotNull(prompt);
        assertTrue(prompt.contains("# Tools"));
        assertTrue(prompt.contains("get_weather"));
        assertTrue(prompt.contains("city"));
    }
    
    @Test
    @Order(4)
    void testSystemPrompt_XMLTags() {
        String prompt = framework.systemPrompt();
        
        // XML 标签格式
        assertTrue(prompt.contains("<tool_calls>"));
        assertTrue(prompt.contains("</tool_calls>"));
        assertTrue(prompt.contains("<thinking>"));
    }
    
    @Test
    @Order(5)
    void testOutputRules() {
        String rules = framework.callingInstructions();
        
        assertNotNull(rules);
        assertTrue(rules.contains("Output Rules"));
        assertTrue(rules.contains("arguments MUST be a JSON string"));
        assertTrue(rules.contains("<tool_calls>"));
    }
    
    @Test
    @Order(6)
    void testFewShotExamples() {
        String examples = framework.systemPrompt();
        
        assertTrue(examples.contains("## Examples"));
        assertTrue(examples.contains("Direct Answer"));
        assertTrue(examples.contains("Single Tool Call"));
        assertTrue(examples.contains("Multiple Parallel Calls"));
        assertTrue(examples.contains("Tool Result Handling"));
    }
    
    // ==================== 4. 解析器测试 ====================
    
    @Test
    @Order(7)
    void testParse_XMLFormat() {
        String response = """
            <thinking>User wants weather</thinking>
            <tool_calls>
            [
              {
                "id": "call_001",
                "type": "function",
                "function": {
                  "name": "get_weather",
                  "arguments": "{\\"city\\": \\"Beijing\\"}"
                }
              }
            ]
            </tool_calls>
            """;
        
        ParseResult result = framework.parse(response);
        
        assertEquals(1, result.toolCalls().size());
        assertEquals("call_001", result.toolCalls().get(0).id());
        assertEquals("get_weather", result.toolCalls().get(0).name());
        assertEquals("Beijing", result.toolCalls().get(0).arguments().get("city"));
    }
    
    @Test
    @Order(8)
    void testParse_WithThinking() {
        String response = "<thinking>Analyzing...</thinking><tool_calls>[{\"id\":\"c1\",\"name\":\"get_weather\",\"arguments\":{}}]</tool_calls>";
        
        ParseResult result = framework.parse(response);
        
        assertNotNull(result.thinking());
        assertTrue(result.thinking().contains("Analyzing"));
    }
    
    @Test
    @Order(9)
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
    @Order(10)
    void testParse_NoToolCalls() {
        String response = "Hello, how can I help you?";
        
        ParseResult result = framework.parse(response);
        
        assertTrue(result.toolCalls().isEmpty());
    }
    
    // ==================== 5. 执行测试 ====================
    
    @Test
    @Order(11)
    void testExecute() {
        ToolCall call = new ToolCall("call_001", "get_weather", 
            Map.of("city", "Beijing", "unit", "celsius"));
        
        List<ToolResult> results = framework.execute(List.of(call));
        
        assertEquals(1, results.size());
        assertTrue(results.get(0).isSuccess());
    }
    
    // ==================== 6. 端到端测试 ====================
    
    @Test
    @Order(12)
    void testEndToEnd() {
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
