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
 * 2. OpenAI 格式工具定义生成
 * 3. OpenAI 格式工具调用解析
 * 4. 工具执行
 * 5. 依赖执行
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
        assertTrue(framework.registry().has("send_email"));
        assertTrue(framework.registry().has("add_numbers"));
    }
    
    @Test
    @Order(2)
    void testGetNames() {
        Set<String> names = framework.registry().getNames();
        assertEquals(6, names.size());
        assertTrue(names.contains("get_weather"));
    }
    
    // ==================== 2. OpenAI 格式工具定义生成测试 ====================
    
    @Test
    @Order(3)
    void testToolsJson_OpenAIFormat() {
        String json = framework.toolsJson();
        assertNotNull(json);
        
        // 验证 OpenAI 格式结构
        assertTrue(json.contains("\"tools\""), "应包含 tools 数组");
        assertTrue(json.contains("\"type\":\"function\""), "应包含 type: function");
        assertTrue(json.contains("\"name\":\"get_weather\""), "应包含函数名");
        assertTrue(json.contains("\"description\""), "应包含描述");
        assertTrue(json.contains("\"parameters\""), "应包含参数定义");
    }
    
    @Test
    @Order(4)
    void testToolsJson_SchemaStructure() {
        String json = framework.toolsJson();
        
        // 验证 JSON Schema 结构
        assertTrue(json.contains("\"type\":\"object\""), "parameters 应为 object 类型");
        assertTrue(json.contains("\"properties\""), "应包含 properties");
        assertTrue(json.contains("\"required\""), "应包含 required");
    }
    
    @Test
    @Order(5)
    void testToolsDescription() {
        String desc = framework.toolsDescription();
        assertNotNull(desc);
        assertTrue(desc.contains("## Available Functions/Tools"));
        assertTrue(desc.contains("get_weather"));
        assertTrue(desc.contains("city"));
        assertTrue(desc.contains("search_hotels"));
    }
    
    // ==================== 3. OpenAI 格式工具调用解析测试 ====================
    
    @Test
    @Order(6)
    void testParse_OpenAIFormat_Full() {
        // OpenAI 完整格式: 包含 type 和嵌套的 function 对象
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
        ToolCall call = calls.get(0);
        assertEquals("call_abc123", call.id());
        assertEquals("get_weather", call.name());
        assertEquals("Beijing", call.arguments().get("city"));
        assertEquals("celsius", call.arguments().get("unit"));
    }
    
    @Test
    @Order(7)
    void testParse_OpenAIFormat_StringArguments() {
        // arguments 是字符串格式
        String response = """
            {
              "tool_calls": [
                {
                  "id": "call_xyz789",
                  "type": "function",
                  "function": {
                    "name": "get_weather",
                    "arguments": "{\\"city\\": \\"Shanghai\\", \\"unit\\": \\"fahrenheit\\"}"
                  }
                }
              ]
            }
            """;
        
        List<ToolCall> calls = framework.parse(response);
        
        assertEquals(1, calls.size());
        assertEquals("call_xyz789", calls.get(0).id());
        assertEquals("Shanghai", calls.get(0).arguments().get("city"));
    }
    
    @Test
    @Order(8)
    void testParse_SimplifiedFormat() {
        // 简化格式: 直接在根节点
        String response = """
            {
              "tool_calls": [
                {
                  "id": "call_001",
                  "name": "get_weather",
                  "arguments": {"city": "Beijing"}
                }
              ]
            }
            """;
        
        List<ToolCall> calls = framework.parse(response);
        
        assertEquals(1, calls.size());
        assertEquals("get_weather", calls.get(0).name());
    }
    
    @Test
    @Order(9)
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
        assertEquals("call_001", calls.get(0).id());
        assertEquals("call_002", calls.get(1).id());
    }
    
    @Test
    @Order(10)
    void testParse_ArrayFormat() {
        String response = """
            [
              {"id": "call_001", "name": "get_weather", "arguments": {"city": "Beijing"}}
            ]
            """;
        
        List<ToolCall> calls = framework.parse(response);
        assertEquals(1, calls.size());
        assertEquals("get_weather", calls.get(0).name());
    }
    
    @Test
    @Order(11)
    void testParse_EmptyResponse() {
        assertTrue(framework.parse("").isEmpty());
        assertTrue(framework.parse(null).isEmpty());
        assertTrue(framework.parse("not json").isEmpty());
        assertTrue(framework.parse("{}").isEmpty());
        assertTrue(framework.parse("{\"text\": \"hello\"}").isEmpty());
    }
    
    // ==================== 4. 执行器测试 ====================
    
    @Test
    @Order(12)
    void testExecuteSingle() {
        ToolCall call = new ToolCall("call_001", "get_weather", 
            Map.of("city", "Shanghai", "unit", "celsius"));
        
        List<ToolResult> results = framework.execute(List.of(call));
        
        assertEquals(1, results.size());
        ToolResult result = results.get(0);
        assertTrue(result.isSuccess());
        assertEquals("call_001", result.id());
        assertEquals("get_weather", result.name());
    }
    
    @Test
    @Order(13)
    void testExecuteMultiple() {
        ToolCall c1 = new ToolCall("call_001", "get_weather", Map.of("city", "Beijing"));
        ToolCall c2 = new ToolCall("call_002", "search_hotels", Map.of("city", "Shanghai"));
        
        List<ToolResult> results = framework.execute(List.of(c1, c2));
        
        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(ToolResult::isSuccess));
    }
    
    @Test
    @Order(14)
    void testExecuteWithDefaultParams() {
        ToolCall call = new ToolCall("call_001", "get_weather", Map.of("city", "Beijing"));
        
        List<ToolResult> results = framework.execute(List.of(call));
        assertTrue(results.get(0).isSuccess());
    }
    
    @Test
    @Order(15)
    void testExecuteIntegerReturn() {
        ToolCall call = new ToolCall("call_001", "add_numbers", Map.of("a", 10, "b", 20));
        
        List<ToolResult> results = framework.execute(List.of(call));
        
        assertTrue(results.get(0).isSuccess());
        assertEquals(30, results.get(0).result());
    }
    
    @Test
    @Order(16)
    void testExecuteMapReturn() {
        ToolCall call = new ToolCall("call_001", "get_config", Map.of("key", "database_url"));
        
        List<ToolResult> results = framework.execute(List.of(call));
        
        assertTrue(results.get(0).isSuccess());
        assertTrue(results.get(0).result() instanceof Map);
    }
    
    @Test
    @Order(17)
    void testExecuteUnknownTool() {
        ToolCall call = new ToolCall("call_001", "unknown_tool", Map.of());
        
        List<ToolResult> results = framework.execute(List.of(call));
        
        assertFalse(results.get(0).isSuccess());
        assertTrue(results.get(0).error().contains("Unknown"));
    }
    
    // ==================== 5. 依赖执行测试 ====================
    
    @Test
    @Order(18)
    void testExecuteParallel() {
        ToolCall c1 = new ToolCall("call_001", "get_weather", Map.of("city", "Beijing"));
        ToolCall c2 = new ToolCall("call_002", "get_weather", Map.of("city", "Shanghai"));
        
        List<ToolResult> results = framework.execute(List.of(c1, c2));
        
        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(ToolResult::isSuccess));
    }
    
    // ==================== 6. One-shot 执行测试 ====================
    
    @Test
    @Order(19)
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
    @Order(20)
    void testExecuteEmptyList() {
        List<ToolResult> results = framework.execute(List.of());
        assertTrue(results.isEmpty());
    }
    
    @Test
    @Order(21)
    void testRegistryGet() {
        var meta = framework.registry().get("get_weather");
        assertNotNull(meta);
        assertEquals("get_weather", meta.name());
    }
    
    @Test
    @Order(22)
    void testResultFormatInstructions() {
        String instructions = framework.toolsDescription();
        assertTrue(instructions.contains("tool_calls"));
        assertTrue(instructions.contains("arguments"));
    }
}
