package com.toolcall;

import com.toolcall.annotation.Tool;
import com.toolcall.model.ToolCall;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 并发和性能测试
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConcurrentTest {
    
    private static ToolCallFramework framework;
    
    @BeforeAll
    static void setup() {
        framework = new ToolCallFramework();
        framework.register(new Object() {
            
            @Tool(name = "identity", description = "返回输入")
            public String identity(String value) {
                return value;
            }
            
            @Tool(name = "add_one", description = "加1")
            public int addOne(int num) {
                return num + 1;
            }
        });
    }
    
    // ========== 1. 连续执行同工具 ==========
    
    @Test
    @Order(1)
    void test_sequential_same_tool() {
        for (int i = 0; i < 10; i++) {
            var call = new ToolCall("c" + i, "add_one", Map.of("num", i));
            var results = framework.execute(List.of(call));
            
            assertEquals(i + 1, results.get(0).result());
        }
    }
    
    // ========== 2. 大参数测试 ==========
    
    @Test
    @Order(2)
    void test_large_string() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("a");
        }
        
        var call = new ToolCall("c1", "identity", Map.of("value", sb.toString()));
        var results = framework.execute(List.of(call));
        
        assertEquals(1000, ((String) results.get(0).result()).length());
    }
    
    // ========== 3. 特殊字符 ==========
    
    @Test
    @Order(3)
    void test_special_characters() {
        String special = "Hello\nWorld\t!@#$%^&*()[]{}\"':;/\\|";
        
        var call = new ToolCall("c1", "identity", Map.of("value", special));
        var results = framework.execute(List.of(call));
        
        assertEquals(special, results.get(0).result());
    }
    
    // ========== 4. Unicode ==========
    
    @Test
    @Order(4)
    void test_unicode() {
        String unicode = "你好世界🌍🎉中文";
        
        var call = new ToolCall("c1", "identity", Map.of("value", unicode));
        var results = framework.execute(List.of(call));
        
        assertEquals(unicode, results.get(0).result());
    }
    
    // ========== 5. JSON 特殊值 ==========
    
    @Test
    @Order(5)
    void test_json_null() {
        // 解析包含 null 的 JSON
        String resp = """
            {"tool_calls":[{"id":"c1","name":"identity","arguments":{"value":null}}]}
            """;
        
        var result = framework.parse(resp);
        assertTrue(result.toolCalls().get(0).arguments().containsKey("value"));
    }
    
    // ========== 6. 数字边界 ==========
    
    @Test
    @Order(6)
    void test_large_number() {
        long max = Long.MAX_VALUE;
        
        var call = new ToolCall("c1", "identity", Map.of("value", String.valueOf(max)));
        var results = framework.execute(List.of(call));
        
        assertEquals(String.valueOf(max), results.get(0).result());
    }
    
    // ========== 7. 负数 ==========
    
    @Test
    @Order(7)
    void test_negative_number() {
        var call = new ToolCall("c1", "add_one", Map.of("num", -100));
        var results = framework.execute(List.of(call));
        
        assertEquals(-99, results.get(0).result());
    }
    
    // ========== 8. 零 ==========
    
    @Test
    @Order(8)
    void test_zero() {
        var call = new ToolCall("c1", "add_one", Map.of("num", 0));
        var results = framework.execute(List.of(call));
        
        assertEquals(1, results.get(0).result());
    }
}
