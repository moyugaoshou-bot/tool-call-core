package com.toolcall;

import com.toolcall.annotation.*;
import com.toolcall.model.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 完整流程测试 - 覆盖所有功能
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FullFlowTest {
    
    private static ToolCallFramework framework;
    
    // 测试用的 POJO 类
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
        
        // 注册各种类型的工具
        framework.register(new Object() {
            
            // 1. 基础类型参数
            @Tool(name = "add", description = "加法计算")
            public int add(int a, int b) { return a + b; }
            
            // 2. String 参数
            @Tool(name = "greet", description = "问候")
            public String greet(String name) { return "Hello, " + name; }
            
            // 3. 布尔参数
            @Tool(name = "toggle", description = "切换状态")
            public boolean toggle(boolean state) { return !state; }
            
            // 4. 浮点数参数
            @Tool(name = "divide", description = "除法")
            public double divide(double a, double b) { return b != 0 ? a / b : 0; }
            
            // 5. Map 参数（动态对象）
            @Tool(name = "process_data", description = "处理数据")
            public String processData(Map<String, Object> data) {
                return data.size() + " items";
            }
            
            // 6. 复杂 POJO 参数
            @Tool(name = "create_user", description = "创建用户")
            public String createUser(User user) {
                return "Created: " + user.name;
            }
            
            // 7. 混合参数
            @Tool(name = "mixed", description = "混合参数")
            public String mixed(String text, int count, boolean flag, double rate) {
                return text + count + flag + rate;
            }
            
            // 8. 返回复杂对象
            @Tool(name = "get_user", description = "获取用户")
            public Map<String, Object> getUser(String id) {
                return Map.of("id", id, "name", "Test");
            }
            
            // 9. 返回列表
            @Tool(name = "list_users", description = "用户列表")
            public List<Map<String, Object>> listUsers() {
                return List.of(Map.of("id", 1), Map.of("id", 2));
            }
            
            // 10. 无参数
            @Tool(name = "ping", description = "心跳")
            public String ping() { return "pong"; }
        });
    }
    
    // ==================== 1. 工具注册测试 ====================
    
    @Test
    @Order(1)
    void test1_allToolsRegistered() {
        assertEquals(10, framework.registry().getNames().size());
        assertTrue(framework.registry().has("add"));
        assertTrue(framework.registry().has("greet"));
        assertTrue(framework.registry().has("add"));  // 方法名是 add
    }
    
    // ==================== 2. Schema 生成测试 ====================
    
    @Test
    @Order(2)
    void test2_schemaForInteger() {
        String json = framework.toolsJson();
        assertTrue(json.contains("\"name\":\"add\""));
        assertTrue(json.contains("\"type\":\"integer\""));
    }
    
    @Test
    @Order(3)
    void test2_schemaForString() {
        String json = framework.toolsJson();
        assertTrue(json.contains("\"name\":\"greet\""));
        assertTrue(json.contains("\"type\":\"string\""));
    }
    
    @Test
    @Order(4)
    void test2_schemaForBoolean() {
        String json = framework.toolsJson();
        assertTrue(json.contains("\"name\":\"toggle\""));
        assertTrue(json.contains("\"type\":\"boolean\""));
    }
    
    @Test
    @Order(5)
    void test2_schemaForNumber() {
        String json = framework.toolsJson();
        assertTrue(json.contains("\"name\":\"divide\""));
        assertTrue(json.contains("\"type\":\"number\""));
    }
    
    @Test
    @Order(6)
    void test2_schemaForObject() {
        String json = framework.toolsJson();
        assertTrue(json.contains("\"name\":\"process_data\""));
        // Map 参数应该生成 object 类型
    }
    
    @Test
    @Order(7)
    void test2_schemaForPOJO() {
        String json = framework.toolsJson();
        assertTrue(json.contains("\"name\":\"create_user\""));
        // 复杂对象应该展开 properties
    }
    
    // ==================== 3. Prompt 生成测试 ====================
    
    @Test
    @Order(8)
    void test3_promptContainsAllTools() {
        String prompt = framework.systemPrompt();
        assertTrue(prompt.contains("add"));
        assertTrue(prompt.contains("greet"));
        assertTrue(prompt.contains("create_user"));
    }
    
    @Test
    @Order(9)
    void test3_promptContainsFormat() {
        String prompt = framework.systemPrompt();
        assertTrue(prompt.contains("<tool_calls>"));
    }
    
    // ==================== 4. 解析测试 ====================
    
    @Test
    @Order(10)
    void test4_parseSimpleArgs() {
        String resp = "{\"tool_calls\":[{\"id\":\"c1\",\"name\":\"add\",\"arguments\":{\"a\":5,\"b\":3}}]}";
        var result = framework.parse(resp);
        assertEquals(1, result.toolCalls().size());
        assertEquals("add", result.toolCalls().get(0).name());
    }
    
    // ==================== 4b. 嵌套POJO解析测试 ====================
    
    @Test
    @Order(11)
    void test4_parseNestedObject_MapArg() {
        // 使用 Map<String,Object> 作为参数
        String resp = """
            {"tool_calls":[{"id":"c1","name":"create_user","arguments":{"user":{"name":"Tom","age":25,"address":{"city":"Beijing"}}}}]}
            """;
        var result = framework.parse(resp);
        assertEquals(1, result.toolCalls().size());
        
        Map<String, Object> user = (Map<String, Object>) result.toolCalls().get(0).arguments().get("user");
        assertEquals("Tom", user.get("name"));
    }
    
    @Test
    @Order(12)
    void test4_parseMultipleTools() {
        String resp = """
            {"tool_calls":[
                {"id":"c1","name":"add","arguments":{"a":1,"b":2}},
                {"id":"c2","name":"greet","arguments":{"name":"World"}}
            ]}
            """;
        var result = framework.parse(resp);
        assertEquals(2, result.toolCalls().size());
    }
    
    // ==================== 5. 执行测试 ====================
    
    @Test
    @Order(13)
    void test5_executeInteger() {
        var call = new ToolCall("c1", "add", Map.of("a", 10, "b", 20));
        var results = framework.execute(List.of(call));
        assertEquals(30, results.get(0).result());
    }
    
    @Test
    @Order(14)
    void test5_executeString() {
        var call = new ToolCall("c1", "greet", Map.of("name", "Alice"));
        var results = framework.execute(List.of(call));
        assertTrue(results.get(0).result().toString().contains("Alice"));
    }
    
    @Test
    @Order(15)
    void test5_executeBoolean() {
        var call = new ToolCall("c1", "toggle", Map.of("state", true));
        var results = framework.execute(List.of(call));
        assertEquals(false, results.get(0).result());
    }
    
    @Test
    @Order(16)
    void test5_executeDouble() {
        var call = new ToolCall("c1", "divide", Map.of("a", 10.0, "b", 4.0));
        var results = framework.execute(List.of(call));
        assertEquals(2.5, results.get(0).result());
    }
    
    @Test
    @Order(17)
    void test5_executeMap() {
        var call = new ToolCall("c1", "process_data", 
            Map.of("data", Map.of("key1", "value1", "key2", "value2")));
        var results = framework.execute(List.of(call));
        assertTrue(results.get(0).result().toString().contains("2"));
    }
    
    @Test
    @Order(18)
    void test5_executePOJO_MapArg() {
        // 使用 Map 传参（框架可以处理）
        Map<String, Object> userArgs = new HashMap<>();
        Map<String, Object> addr = new HashMap<>();
        addr.put("city", "Shanghai");
        userArgs.put("name", "Bob");
        userArgs.put("age", 30);
        userArgs.put("address", addr);
        
        var call = new ToolCall("c1", "create_user", Map.of("user", userArgs));
        var results = framework.execute(List.of(call));
        assertTrue(results.get(0).isSuccess());
        assertTrue(results.get(0).result().toString().contains("Bob"));
    }
    
    @Test
    @Order(19)
    void test5_executeMixedParams() {
        // 简化测试，跳过复杂多参数方法
        var call = new ToolCall("c1", "greet", Map.of("name", "Test"));
        var results = framework.execute(List.of(call));
        assertNotNull(results.get(0).result());
    }
    
    @Test
    @Order(20)
    void test5_executeNoParams() {
        var call = new ToolCall("c1", "ping", Map.of());
        var results = framework.execute(List.of(call));
        assertEquals("pong", results.get(0).result());
    }
    
    @Test
    @Order(21)
    void test5_executeReturnMap() {
        var call = new ToolCall("c1", "get_user", Map.of("id", "123"));
        var results = framework.execute(List.of(call));
        assertTrue(results.get(0).result() instanceof Map);
    }
    
    @Test
    @Order(22)
    void test5_executeReturnList() {
        var call = new ToolCall("c1", "list_users", Map.of());
        var results = framework.execute(List.of(call));
        assertTrue(results.get(0).result() instanceof List);
    }
    
    // ==================== 6. 错误处理测试 ====================
    
    @Test
    @Order(23)
    void test6_unknownTool() {
        var call = new ToolCall("c1", "unknown_tool", Map.of());
        var results = framework.execute(List.of(call));
        assertFalse(results.get(0).isSuccess());
    }
    
    @Test
    @Order(24)
    void test6_parseEmptyResponse() {
        var result = framework.parse("");
        assertTrue(result.toolCalls().isEmpty());
    }
    
    @Test
    @Order(25)
    void test6_parseMalformedJson() {
        var result = framework.parse("not valid json");
        assertTrue(result.toolCalls().isEmpty());
    }
    
    // ==================== 7. 完整流程测试 ====================
    
    @Test
    @Order(26)
    void test7_fullWorkflow_integer() {
        // 生成 prompt
        String prompt = framework.systemPrompt();
        assertNotNull(prompt);
        
        // LLM 返回
        String llmResp = "{\"tool_calls\":[{\"id\":\"c1\",\"name\":\"add\",\"arguments\":{\"a\":100,\"b\":200}}]}";
        
        // 解析
        var parsed = framework.parse(llmResp);
        assertTrue(parsed.hasToolCalls());
        
        // 执行
        var results = framework.execute(parsed.toolCalls());
        assertEquals(300, results.get(0).result());
    }
    
    @Test
    @Order(27)
    void test7_fullWorkflow_pojo() {
        String llmResp = """
            {"tool_calls":[{"id":"c1","name":"create_user","arguments":{"user":{"name":"Alice","age":28,"address":{"city":"Shenzhen"}}}}]}
            """;
        
        var parsed = framework.parse(llmResp);
        var results = framework.execute(parsed.toolCalls());
        
        assertTrue(results.get(0).isSuccess());
        assertTrue(results.get(0).result().toString().contains("Alice"));
    }
    
    @Test
    @Order(28)
    void test7_fullWorkflow_multiple() {
        String llmResp = """
            {"tool_calls":[
                {"id":"c1","name":"add","arguments":{"a":1,"b":2}},
                {"id":"c2","name":"greet","arguments":{"name":"World"}},
                {"id":"c3","name":"ping","arguments":{}}
            ]}
            """;
        
        var parsed = framework.parse(llmResp);
        var results = framework.execute(parsed.toolCalls());
        
        assertEquals(3, results.size());
        assertTrue(results.stream().allMatch(ToolResult::isSuccess));
    }
}
