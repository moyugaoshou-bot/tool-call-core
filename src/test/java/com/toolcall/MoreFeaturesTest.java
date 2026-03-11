package com.toolcall;

import com.toolcall.annotation.*;
import com.toolcall.model.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 更多功能测试用例
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MoreFeaturesTest {
    
    private static ToolCallFramework framework;
    
    @BeforeAll
    static void setup() {
        framework = new ToolCallFramework();
        
        framework.register(new Object() {
            
            // 带默认值的参数
            @Tool(name = "search", description = "搜索")
            public List<String> search(String keyword, int limit, String sort) {
                return List.of("result1", "result2");
            }
            
            // 返回复杂对象
            @Tool(name = "get_user_info", description = "获取用户信息")
            public Map<String, Object> getUserInfo(String id) {
                return Map.of("id", id, "name", "Test", "active", true);
            }
            
            // 返回列表
            @Tool(name = "list_items", description = "列表")
            public List<String> listItems() {
                return List.of("a", "b", "c");
            }
            
            // 无返回值
            @Tool(name = "notify", description = "通知")
            public void notify(String message) {
                System.out.println("Notified: " + message);
            }
            
            // 布尔返回值
            @Tool(name = "is_valid", description = "验证")
            public boolean isValid(String value) {
                return "valid".equals(value);
            }
        });
    }
    
    // ==================== 1. 参数默认值测试 ====================
    
    @Test
    @Order(1)
    void test_defaultParams_limit() {
        // 只传 keyword，limit 使用默认值
        var call = new ToolCall("c1", "search", Map.of("keyword", "test"));
        var results = framework.execute(List.of(call));
        
        assertTrue(results.get(0).isSuccess());
    }
    
    @Test
    @Order(2)
    void test_defaultParams_partial() {
        // 传部分参数（只传keyword）
        var call = new ToolCall("c1", "search", Map.of("keyword", "test"));
        var results = framework.execute(List.of(call));
        
        assertTrue(results.get(0).isSuccess());
    }
    
    // ==================== 2. 返回值类型测试 ====================
    
    @Test
    @Order(3)
    void test_returnMap() {
        var call = new ToolCall("c1", "get_user_info", Map.of("id", "123"));
        var results = framework.execute(List.of(call));
        
        assertTrue(results.get(0).isSuccess());
        assertTrue(results.get(0).result() instanceof Map);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) results.get(0).result();
        assertEquals("123", user.get("id"));
        assertEquals("Test", user.get("name"));
        assertEquals(true, user.get("active"));
    }
    
    @Test
    @Order(4)
    void test_returnList() {
        var call = new ToolCall("c1", "list_items", Map.of());
        var results = framework.execute(List.of(call));
        
        assertTrue(results.get(0).isSuccess());
        assertTrue(results.get(0).result() instanceof List);
        
        @SuppressWarnings("unchecked")
        List<String> items = (List<String>) results.get(0).result();
        assertEquals(3, items.size());
    }
    
    @Test
    @Order(5)
    void test_returnBoolean() {
        var call = new ToolCall("c1", "is_valid", Map.of("value", "valid"));
        var results = framework.execute(List.of(call));
        
        assertTrue(results.get(0).isSuccess());
        assertEquals(true, results.get(0).result());
        
        // 测试返回 false
        var call2 = new ToolCall("c2", "is_valid", Map.of("value", "invalid"));
        var results2 = framework.execute(List.of(call2));
        assertEquals(false, results2.get(0).result());
    }
    
    // ==================== 3. 执行时间测试 ====================
    
    @Test
    @Order(6)
    void test_executionTimeRecorded() {
        var call = new ToolCall("c1", "list_items", Map.of());
        var results = framework.execute(List.of(call));
        
        assertTrue(results.get(0).durationMs() >= 0);
    }
    
    // ==================== 4. 多工具并行测试 ====================
    
    @Test
    @Order(7)
    void test_parallelExecution_order() {
        var call1 = new ToolCall("c1", "list_items", Map.of());
        var call2 = new ToolCall("c2", "list_items", Map.of());
        var call3 = new ToolCall("c3", "list_items", Map.of());
        
        var results = framework.execute(List.of(call1, call2, call3));
        
        assertEquals(3, results.size());
        assertTrue(results.stream().allMatch(ToolResult::isSuccess));
    }
    
    // ==================== 5. 错误信息测试 ====================
    
    @Test
    @Order(8)
    void test_errorMessageNotEmpty() {
        var call = new ToolCall("c1", "nonexistent", Map.of());
        var results = framework.execute(List.of(call));
        
        assertFalse(results.get(0).isSuccess());
        assertNotNull(results.get(0).error());
        assertTrue(results.get(0).error().length() > 0);
    }
    
    // ==================== 6. 工具元数据测试 ====================
    
    @Test
    @Order(9)
    void test_registryGetMeta() {
        var meta = framework.registry().get("list_items");
        assertNotNull(meta);
        assertEquals("list_items", meta.name());
    }
    
    // ==================== 7. XML格式解析测试 ====================
    
    @Test
    @Order(10)
    void test_parseXML_withType() {
        String resp = """
            <tool_calls>
            [
              {"id": "call_x", "type": "function", "function": {"name": "list_items", "arguments": {}}}
            ]
            </tool_calls>
            """;
        
        var result = framework.parse(resp);
        assertEquals(1, result.toolCalls().size());
        assertEquals("call_x", result.toolCalls().get(0).id());
        assertEquals("list_items", result.toolCalls().get(0).name());
    }
    
    @Test
    @Order(11)
    void test_parseXML_cleanText() {
        String resp = """
            Some text before
            <tool_calls>[{"id":"c1","name":"list_items","arguments":{}}]</tool_calls>
            Some text after
            """;
        
        var result = framework.parse(resp);
        assertEquals(1, result.toolCalls().size());
    }
    
    // ==================== 8. thinking标签测试 ====================
    
    @Test
    @Order(12)
    void test_thinkingExtracted() {
        String resp = "<thinking>I need to call list_items</thinking><tool_calls>[{\"id\":\"c1\",\"name\":\"list_items\",\"arguments\":{}}]</tool_calls>";
        
        var result = framework.parse(resp);
        assertEquals(1, result.toolCalls().size());
        assertNotNull(result.thinking());
        assertTrue(result.thinking().contains("list_items"));
    }
    
    // ==================== 9. 空参数测试 ====================
    
    @Test
    @Order(13)
    void test_emptyArguments() {
        String resp = "{\"tool_calls\":[{\"id\":\"c1\",\"name\":\"list_items\",\"arguments\":{}}]}";
        
        var result = framework.parse(resp);
        assertEquals(1, result.toolCalls().size());
        assertTrue(result.toolCalls().get(0).arguments().isEmpty());
    }
    
    // ==================== 10. 完整流程测试 ====================
    
    @Test
    @Order(14)
    void test_completeWorkflow_mapResult() {
        // 完整流程：注册 -> 生成prompt -> LLM返回 -> 解析 -> 执行 -> 验证结果
        
        // 1. 验证工具存在
        assertTrue(framework.registry().has("get_user_info"));
        
        // 2. 验证Schema
        String json = framework.toolsJson();
        assertTrue(json.contains("get_user_info"));
        
        // 3. LLM返回
        String llmResp = """
            {"tool_calls":[{"id":"workflow_test","name":"get_user_info","arguments":{"id":"user_123"}}]}
            """;
        
        // 4. 解析
        var parsed = framework.parse(llmResp);
        assertTrue(parsed.hasToolCalls());
        
        // 5. 执行
        var results = framework.execute(parsed.toolCalls());
        
        // 6. 验证结果
        assertEquals(1, results.size());
        assertTrue(results.get(0).isSuccess());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) results.get(0).result();
        assertEquals("user_123", user.get("id"));
    }
}
