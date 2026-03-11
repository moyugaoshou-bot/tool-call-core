package com.toolcall;

import com.toolcall.annotation.*;
import com.toolcall.model.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 严格测试 - 验证每个环节的准确性
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StrictValidationTest {
    
    private static ToolCallFramework framework;
    
    // 嵌套类定义
    public static class User {
        public String name;
        public int age;
        public Address address;
    }
    
    public static class Address {
        public String city;
        public String street;
    }
    
    @BeforeAll
    static void setup() {
        framework = new ToolCallFramework();
        framework.register(new Object() {
            
            @Tool(name = "create_user", description = "创建用户")
            public String createUser(User user) {
                return user.name + "|" + user.age + "|" + 
                       (user.address != null ? user.address.city + "|" + user.address.street : "null");
            }
            
            @Tool(name = "add", description = "加法")
            public int add(int a, int b) {
                return a + b;
            }
            
            @Tool(name = "greet", description = "问候")
            public String greet(String name) {
                return "Hello, " + name;
            }
        });
    }
    
    // ==================== 1. 工具注册严格验证 ====================
    
    @Test
    @Order(1)
    void test_register_exactCount() {
        // 精确数量
        assertEquals(3, framework.registry().getNames().size());
    }
    
    @Test
    @Order(2)
    void test_register_exactNames() {
        // 精确名称
        var names = framework.registry().getNames();
        assertTrue(names.contains("create_user"));
        assertTrue(names.contains("add"));
        assertTrue(names.contains("greet"));
    }
    
    // ==================== 2. Schema 严格验证 ====================
    
    @Test
    @Order(3)
    void test_schema_hasExactTools() {
        String json = framework.toolsJson();
        
        // 验证包含所有工具
        assertTrue(json.contains("\"name\":\"create_user\""));
        assertTrue(json.contains("\"name\":\"add\""));
        assertTrue(json.contains("\"name\":\"greet\""));
        
        // 验证格式
        assertTrue(json.contains("\"type\":\"function\""));
        assertTrue(json.contains("\"parameters\""));
    }
    
    @Test
    @Order(4)
    void test_schema_add_hasIntegerParams() {
        String json = framework.toolsJson();
        
        // add 工具应该有 integer 类型参数
        assertTrue(json.contains("\"name\":\"add\""));
        assertTrue(json.contains("\"a\""));
        assertTrue(json.contains("\"b\""));
    }
    
    @Test
    @Order(5)
    void test_schema_nestedPOJO_hasProperties() {
        String json = framework.toolsJson();
        
        // create_user 的参数应该有嵌套 properties
        assertTrue(json.contains("\"name\":\"create_user\""));
        // 应该有 name, age, address 属性
        assertTrue(json.contains("\"name\""));
        assertTrue(json.contains("\"age\""));
        assertTrue(json.contains("\"address\""));
    }
    
    // ==================== 3. Prompt 严格验证 ====================
    
    @Test
    @Order(6)
    void test_prompt_hasAllTools() {
        String prompt = framework.systemPrompt();
        
        // 必须包含所有工具名称
        assertTrue(prompt.contains("create_user"));
        assertTrue(prompt.contains("add"));
        assertTrue(prompt.contains("greet"));
        
        // 必须包含格式说明
        assertTrue(prompt.contains("<tool_calls>"));
        assertTrue(prompt.contains("arguments"));
    }
    
    // ==================== 4. 解析严格验证 ====================
    
    @Test
    @Order(7)
    void test_parse_exactId() {
        String resp = "{\"tool_calls\":[{\"id\":\"call_abc123\",\"name\":\"greet\",\"arguments\":{\"name\":\"Tom\"}}]}";
        var result = framework.parse(resp);
        
        assertEquals(1, result.toolCalls().size());
        assertEquals("call_abc123", result.toolCalls().get(0).id());
    }
    
    @Test
    @Order(8)
    void test_parse_exactName() {
        String resp = "{\"tool_calls\":[{\"id\":\"c1\",\"name\":\"add\",\"arguments\":{\"a\":5,\"b\":3}}]}";
        var result = framework.parse(resp);
        
        assertEquals("add", result.toolCalls().get(0).name());
    }
    
    @Test
    @Order(9)
    void test_parse_exactArguments() {
        String resp = "{\"tool_calls\":[{\"id\":\"c1\",\"name\":\"add\",\"arguments\":{\"a\":10,\"b\":20}}]}";
        var result = framework.parse(resp);
        
        var args = result.toolCalls().get(0).arguments();
        assertEquals(10, args.get("a"));
        assertEquals(20, args.get("b"));
    }
    
    @Test
    @Order(10)
    void test_parse_nestedObject() {
        String resp = """
            {"tool_calls":[{"id":"c1","name":"create_user","arguments":{"user":{"name":"Alice","age":28,"address":{"city":"Shanghai","street":"Main St"}}}}]}
            """;
        var result = framework.parse(resp);
        
        var user = (Map<String, Object>) result.toolCalls().get(0).arguments().get("user");
        assertEquals("Alice", user.get("name"));
        assertEquals(28, user.get("age"));
        
        var address = (Map<String, Object>) user.get("address");
        assertEquals("Shanghai", address.get("city"));
        assertEquals("Main St", address.get("street"));
    }
    
    // ==================== 5. 执行严格验证 ====================
    
    @Test
    @Order(11)
    void test_execute_add_exactResult() {
        var call = new ToolCall("c1", "add", Map.of("a", 15, "b", 25));
        var results = framework.execute(List.of(call));
        
        // 精确结果验证
        assertEquals(40, results.get(0).result());
    }
    
    @Test
    @Order(12)
    void test_execute_greet_exactResult() {
        var call = new ToolCall("c1", "greet", Map.of("name", "Bob"));
        var results = framework.execute(List.of(call));
        
        // 精确结果验证
        assertEquals("Hello, Bob", results.get(0).result());
    }
    
    @Test
    @Order(13)
    void test_execute_nestedPOJO_exactResult() {
        // 构建嵌套参数
        Map<String, Object> address = new HashMap<>();
        address.put("city", "Beijing");
        address.put("street", "Xicheng");
        
        Map<String, Object> user = new HashMap<>();
        user.put("name", "Charlie");
        user.put("age", 30);
        user.put("address", address);
        
        var call = new ToolCall("c1", "create_user", Map.of("user", user));
        var results = framework.execute(List.of(call));
        
        // 精确结果验证
        assertEquals("Charlie|30|Beijing|Xicheng", results.get(0).result());
    }
    
    // ==================== 6. 完整流程严格验证 ====================
    
    @Test
    @Order(14)
    void test_fullFlow_nestedPOJO() {
        // 1. 验证工具注册
        assertEquals(3, framework.registry().getNames().size());
        
        // 2. 验证 Schema 包含嵌套
        String json = framework.toolsJson();
        assertTrue(json.contains("\"address\""));
        
        // 3. 解析嵌套参数
        String llmResp = """
            {"tool_calls":[{"id":"test123","name":"create_user","arguments":{"user":{"name":"David","age":35,"address":{"city":"Shenzhen","street":"Nanshan"}}}}]}
            """;
        var parsed = framework.parse(llmResp);
        
        // 4. 验证解析结果
        assertEquals("test123", parsed.toolCalls().get(0).id());
        assertEquals("create_user", parsed.toolCalls().get(0).name());
        
        // 5. 执行
        var execResults = framework.execute(parsed.toolCalls());
        
        // 6. 验证执行结果
        assertTrue(execResults.get(0).isSuccess());
        assertEquals("David|35|Shenzhen|Nanshan", execResults.get(0).result());
    }
    
    // ==================== 7. 错误处理严格验证 ====================
    
    @Test
    @Order(15)
    void test_error_unknownTool() {
        var call = new ToolCall("c1", "unknown", Map.of());
        var results = framework.execute(List.of(call));
        
        assertFalse(results.get(0).isSuccess());
        assertNotNull(results.get(0).error());
    }
    
    @Test
    @Order(16)
    void test_error_executionTimeRecorded() {
        var call = new ToolCall("c1", "greet", Map.of("name", "Test"));
        var results = framework.execute(List.of(call));
        
        // 验证执行时间被记录
        assertTrue(results.get(0).durationMs() >= 0);
    }
}
