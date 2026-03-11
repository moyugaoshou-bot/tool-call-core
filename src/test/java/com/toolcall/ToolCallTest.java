package com.toolcall;

import com.toolcall.model.ToolCall;
import com.toolcall.model.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolCallTest {
    
    @Test
    void testRegisterAndToolsJson() {
        ToolCallFramework tc = new ToolCallFramework();
        tc.register(new DemoTools());
        
        assertTrue(tc.registry().has("get_weather"));
        assertTrue(tc.registry().has("search_hotels"));
        
        String json = tc.toolsJson();
        assertTrue(json.contains("get_weather"));
        assertTrue(json.contains("search_hotels"));
    }
    
    @Test
    void testToolsDescription() {
        ToolCallFramework tc = new ToolCallFramework();
        tc.register(new DemoTools());
        
        String desc = tc.toolsDescription();
        assertTrue(desc.contains("get_weather"));
        assertTrue(desc.contains("city"));
    }
    
    @Test
    void testParseToolCalls() {
        ToolCallFramework tc = new ToolCallFramework();
        tc.register(new DemoTools());
        
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
        
        List<ToolCall> calls = tc.parse(response);
        assertEquals(1, calls.size());
        assertEquals("call_001", calls.get(0).id());
        assertEquals("get_weather", calls.get(0).name());
        assertEquals("Beijing", calls.get(0).arguments().get("city"));
    }
    
    @Test
    void testExecute() {
        ToolCallFramework tc = new ToolCallFramework();
        tc.register(new DemoTools());
        
        ToolCall call = new ToolCall("call_001", "get_weather", Map.of("city", "Shanghai", "unit", "celsius"));
        List<ToolResult> results = tc.execute(List.of(call));
        
        assertEquals(1, results.size());
        assertTrue(results.get(0).isSuccess());
        assertTrue(results.get(0).result().toString().contains("天气"));
    }
    
    @Test
    void testDependency() {
        ToolCallFramework tc = new ToolCallFramework();
        tc.register(new DemoTools());
        
        // Two independent calls should run in parallel
        ToolCall c1 = new ToolCall("call_001", "get_weather", Map.of("city", "Beijing"));
        ToolCall c2 = new ToolCall("call_002", "search_hotels", Map.of("city", "Shanghai"));
        
        List<ToolResult> results = tc.execute(List.of(c1, c2));
        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(ToolResult::isSuccess));
    }
}
