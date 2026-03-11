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
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ToolCallTest {
    
    private static ToolCallFramework framework;
    
    @BeforeAll
    static void setup() {
        framework = new ToolCallFramework();
        framework.register(new DemoTools());
    }
    
    // ==================== 注册表测试 ====================
    
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
    
    // ==================== 工具定义生成测试 ====================
    
    @Test
    @Order(3)
    void testToolsJson() {
        String json = framework.toolsJson();
        assertNotNull(json);
        assertTrue(json.contains("\"tools\""));
        assertTrue(json.contains("get_weather"));
        assertTrue(json.contains("search_hotels"));
        assertTrue(json.contains("book_hotel"));
    }
    
    @Test
    @Order(4)
    void testToolsJsonStructure() {
        String json = framework.toolsJson();
        // 验证 JSON 结构包含必要字段
        assertTrue(json.contains("\"type\":\"function\""));
        assertTrue(json.contains("\"name\":\"get_weather\""));
        assertTrue(json.contains("\"parameters\""));
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
    
    // ==================== 解析器测试 ====================
    
    @Test
    @Order(6)
    void testParseToolCalls() {
        String response = """
            {
              "tool_calls": [
                {
                  "id": "call_001",
                  "name": "get_weather",
                  "arguments": {"city": "Beijing", "unit": "celsius"}
                }
              ]
            }
            """;
        
        List<ToolCall> calls = framework.parse(response);
        assertEquals(1, calls.size());
        
        ToolCall call = calls.get(0);
        assertEquals("call_001", call.id());
        assertEquals("get_weather", call.name());
        assertEquals("Beijing", call.arguments().get("city"));
        assertEquals("celsius", call.arguments().get("unit"));
    }
    
    @Test
    @Order(7)
    void testParseMultipleToolCalls() {
        String response = """
            {
              "tool_calls": [
                {"id": "call_001", "name": "get_weather", "arguments": {"city": "Beijing"}},
                {"id": "call_002", "name": "search_hotels", "arguments": {"city": "Shanghai", "min_price": 300}}
              ]
            }
            """;
        
        List<ToolCall> calls = framework.parse(response);
        assertEquals(2, calls.size());
        assertEquals("call_001", calls.get(0).id());
        assertEquals("call_002", calls.get(1).id());
    }
    
    @Test
    @Order(8)
    void testParseArrayFormat() {
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
    @Order(9)
    void testParseWithStringArguments() {
        String response = """
            {
              "tool_calls": [
                {
                  "id": "call_001",
                  "name": "get_weather",
                  "arguments": "{\\"city\\": \\"Beijing\\", \\"unit\\": \\"celsius\\"}"
                }
              ]
            }
            """;
        
        List<ToolCall> calls = framework.parse(response);
        assertEquals(1, calls.size());
        assertEquals("Beijing", calls.get(0).arguments().get("city"));
    }
    
    @Test
    @Order(10)
    void testParseEmptyResponse() {
        assertTrue(framework.parse("").isEmpty());
        assertTrue(framework.parse(null).isEmpty());
        assertTrue(framework.parse("not json").isEmpty());
    }
    
    // ==================== 执行器测试 ====================
    
    @Test
    @Order(11)
    void testExecuteSingle() {
        ToolCall call = new ToolCall("call_001", "get_weather", 
            Map.of("city", "Shanghai", "unit", "celsius"));
        
        List<ToolResult> results = framework.execute(List.of(call));
        
        assertEquals(1, results.size());
        ToolResult result = results.get(0);
        assertTrue(result.isSuccess());
        assertEquals("call_001", result.id());
        assertEquals("get_weather", result.name());
        assertTrue(result.result().toString().contains("天气"));
    }
    
    @Test
    @Order(12)
    void testExecuteMultiple() {
        ToolCall c1 = new ToolCall("call_001", "get_weather", Map.of("city", "Beijing"));
        ToolCall c2 = new ToolCall("call_002", "search_hotels", Map.of("city", "Shanghai"));
        
        List<ToolResult> results = framework.execute(List.of(c1, c2));
        
        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(ToolResult::isSuccess));
    }
    
    @Test
    @Order(13)
    void testExecuteWithDefaultParams() {
        // 只传必需参数，可选参数使用默认值
        ToolCall call = new ToolCall("call_001", "get_weather", 
            Map.of("city", "Beijing"));
        
        List<ToolResult> results = framework.execute(List.of(call));
        
        assertTrue(results.get(0).isSuccess());
    }
    
    @Test
    @Order(14)
    void testExecuteIntegerReturn() {
        ToolCall call = new ToolCall("call_001", "add_numbers", 
            Map.of("a", 10, "b", 20));
        
        List<ToolResult> results = framework.execute(List.of(call));
        
        assertTrue(results.get(0).isSuccess());
        assertEquals(30, results.get(0).result());
    }
    
    @Test
    @Order(15)
    void testExecuteMapReturn() {
        ToolCall call = new ToolCall("call_001", "get_config", 
            Map.of("key", "database_url"));
        
        List<ToolResult> results = framework.execute(List.of(call));
        
        assertTrue(results.get(0).isSuccess());
        assertTrue(results.get(0).result() instanceof Map);
    }
    
    @Test
    @Order(16)
    void testExecuteUnknownTool() {
        ToolCall call = new ToolCall("call_001", "unknown_tool", Map.of());
        
        List<ToolResult> results = framework.execute(List.of(call));
        
        assertEquals(1, results.size());
        assertFalse(results.get(0).isSuccess());
        assertNotNull(results.get(0).error());
        assertTrue(results.get(0).error().contains("Unknown"));
    }
    
    // ==================== 依赖执行测试 ====================
    
    @Test
    @Order(17)
    void testExecuteParallel() {
        // 无依赖的调用应并行执行
        ToolCall c1 = new ToolCall("call_001", "get_weather", Map.of("city", "Beijing"));
        ToolCall c2 = new ToolCall("call_002", "get_weather", Map.of("city", "Shanghai"));
        ToolCall c3 = new ToolCall("call_003", "get_weather", Map.of("city", "Guangzhou"));
        
        long start = System.currentTimeMillis();
        List<ToolResult> results = framework.execute(List.of(c1, c2, c3));
        long duration = System.currentTimeMillis() - start;
        
        assertEquals(3, results.size());
        assertTrue(results.stream().allMatch(ToolResult::isSuccess));
    }
    
    // ==================== One-shot 执行测试 ====================
    
    @Test
    @Order(18)
    void testExecuteOneShot() {
        String response = """
            {
              "tool_calls": [
                {"id": "call_001", "name": "add_numbers", "arguments": {"a": 100, "b": 200}}
              ]
            }
            """;
        
        List<ToolResult> results = framework.execute(response);
        
        assertEquals(1, results.size());
        assertTrue(results.get(0).isSuccess());
        assertEquals(300, results.get(0).result());
    }
    
    // ==================== 边界测试 ====================
    
    @Test
    @Order(19)
    void testExecuteEmptyList() {
        List<ToolResult> results = framework.execute(List.of());
        assertTrue(results.isEmpty());
    }
    
    @Test
    @Order(20)
    void testRegistryGet() {
        var meta = framework.registry().get("get_weather");
        assertNotNull(meta);
        assertEquals("get_weather", meta.name());
        assertNotNull(meta.parameters());
    }
}
