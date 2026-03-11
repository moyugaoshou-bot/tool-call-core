package com.toolcall;

import com.toolcall.annotation.Tool;
import com.toolcall.model.ToolCall;
import com.toolcall.model.ToolResult;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 边界情况和错误处理测试
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BoundaryTest {
    
    private static ToolCallFramework framework;
    
    // 复杂嵌套类
    public static class Person {
        public String name;
        public int age;
        public Contact contact;
    }
    
    public static class Contact {
        public String email;
        public Phone phone;
    }
    
    public static class Phone {
        public String mobile;
        public String home;
    }
    
    @BeforeAll
    static void setup() {
        framework = new ToolCallFramework();
        framework.register(new Object() {
            
            @Tool(name = "concat", description = "连接字符串")
            public String concat(String a, String b, String c) {
                return a + b + c;
            }
            
            @Tool(name = "sum_array", description = "求和数组")
            public int sumArray(List<Integer> numbers) {
                return numbers.stream().mapToInt(Integer::intValue).sum();
            }
            
            @Tool(name = "create_person", description = "创建人员")
            public String createPerson(Person person) {
                return person.name + ":" + person.age + ":" + 
                       (person.contact != null ? person.contact.email : "no-email");
            }
            
            @Tool(name = "empty_tool", description = "空参数工具")
            public String emptyTool() {
                return "ok";
            }
            
            @Tool(name = "bool_test", description = "布尔测试")
            public boolean boolTest(boolean flag) {
                return !flag;
            }
        });
    }
    
    // ========== 1. 数组参数测试 ==========
    
    @Test
    @Order(1)
    void test_array_param() {
        var call = new ToolCall("c1", "sum_array", Map.of("numbers", List.of(1, 2, 3, 4, 5)));
        var results = framework.execute(List.of(call));
        
        assertEquals(15, results.get(0).result()); // 1+2+3+4+5=15
    }
    
    @Test
    @Order(2)
    void test_array_empty() {
        var call = new ToolCall("c1", "sum_array", Map.of("numbers", List.of()));
        var results = framework.execute(List.of(call));
        
        assertEquals(0, results.get(0).result());
    }
    
    // ========== 2. 多字符串参数 ==========
    
    @Test
    @Order(3)
    void test_multiple_strings() {
        var call = new ToolCall("c1", "concat", Map.of("a", "Hello", "b", " ", "c", "World"));
        var results = framework.execute(List.of(call));
        
        assertEquals("Hello World", results.get(0).result());
    }
    
    // ========== 3. 深层嵌套 ==========
    
    @Test
    @Order(4)
    void test_deep_nested() {
        Map<String, Object> phone = new HashMap<>();
        phone.put("mobile", "13800000000");
        phone.put("home", "010-12345678");
        
        Map<String, Object> contact = new HashMap<>();
        contact.put("email", "test@example.com");
        contact.put("phone", phone);
        
        Map<String, Object> person = new HashMap<>();
        person.put("name", "Alice");
        person.put("age", 30);
        person.put("contact", contact);
        
        var call = new ToolCall("c1", "create_person", Map.of("person", person));
        var results = framework.execute(List.of(call));
        
        assertEquals("Alice:30:test@example.com", results.get(0).result());
    }
    
    // ========== 4. 空参数 ==========
    
    @Test
    @Order(5)
    void test_no_args() {
        var call = new ToolCall("c1", "empty_tool", Map.of());
        var results = framework.execute(List.of(call));
        
        assertTrue(results.get(0).isSuccess());
        assertEquals("ok", results.get(0).result());
    }
    
    // ========== 5. 布尔参数 ==========
    
    @Test
    @Order(6)
    void test_boolean_true() {
        var call = new ToolCall("c1", "bool_test", Map.of("flag", true));
        var results = framework.execute(List.of(call));
        
        assertEquals(false, results.get(0).result());
    }
    
    @Test
    @Order(7)
    void test_boolean_false() {
        var call = new ToolCall("c1", "bool_test", Map.of("flag", false));
        var results = framework.execute(List.of(call));
        
        assertEquals(true, results.get(0).result());
    }
    
    // ========== 6. 解析边界 ==========
    
    @Test
    @Order(8)
    void test_parse_empty_args() {
        String resp = "{\"tool_calls\":[{\"id\":\"c1\",\"name\":\"empty_tool\",\"arguments\":{}}]}";
        
        var result = framework.parse(resp);
        assertEquals(1, result.toolCalls().size());
        assertTrue(result.toolCalls().get(0).arguments().isEmpty());
    }
    
    @Test
    @Order(9)
    void test_parse_array_args() {
        String resp = """
            {"tool_calls":[{"id":"c1","name":"sumArray","arguments":{"numbers":[10,20,30]}}]}
            """;
        
        var result = framework.parse(resp);
        
        List<Integer> nums = (List<Integer>) result.toolCalls().get(0).arguments().get("numbers");
        assertEquals(3, nums.size());
        assertEquals(10, nums.get(0));
    }
    
    // ========== 7. 执行时间 ==========
    
    @Test
    @Order(10)
    void test_duration_positive() {
        var call = new ToolCall("c1", "empty_tool", Map.of());
        var results = framework.execute(List.of(call));
        
        assertTrue(results.get(0).durationMs() >= 0);
    }
    
    // ========== 8. 多工具调用 ==========
    
    @Test
    @Order(11)
    void test_multiple_tools_order() {
        var call1 = new ToolCall("c1", "empty_tool", Map.of());
        var call2 = new ToolCall("c2", "empty_tool", Map.of());
        var call3 = new ToolCall("c3", "empty_tool", Map.of());
        
        var results = framework.execute(List.of(call1, call2, call3));
        
        assertEquals(3, results.size());
        assertEquals("c1", results.get(0).id());
        assertEquals("c2", results.get(1).id());
        assertEquals("c3", results.get(2).id());
    }
    
    // ========== 9. Schema 完整性 ==========
    
    @Test
    @Order(12)
    void test_schema_has_all_tools() {
        String json = framework.toolsJson();
        
        assertTrue(json.contains("concat"));
        assertTrue(json.contains("sum_array"));
        assertTrue(json.contains("create_person"));
        assertTrue(json.contains("empty_tool"));
        assertTrue(json.contains("bool_test"));
    }
    
    // ========== 10. 错误场景 ==========
    
    @Test
    @Order(13)
    void test_missing_required_arg() {
        // 缺少必需参数
        var call = new ToolCall("c1", "concat", Map.of("a", "Hello"));
        var results = framework.execute(List.of(call));
        
        // 应该使用默认值或报错
        assertNotNull(results.get(0).result());
    }
}
