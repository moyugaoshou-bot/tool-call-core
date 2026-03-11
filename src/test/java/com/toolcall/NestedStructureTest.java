package com.toolcall;

import com.toolcall.schema.SchemaGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 复杂嵌套结构体测试
 */
class NestedStructureTest {
    
    // 三层嵌套：Order -> Item -> Category
    static class Category {
        String name;
        int level;
    }
    
    static class Item {
        String itemName;
        double price;
        Category category;  // 嵌套 Category
        List<String> tags;  // 嵌套 List
    }
    
    static class Order {
        String orderId;
        List<Item> items;           // 嵌套 List<Item>
        Set<String> coupons;        // 嵌套 Set
        Map<String, Address> addresses; // 嵌套 Map
    }
    
    static class Address {
        String streetName;
        String cityName;
    }
    
    @Test
    void testDeepNestedSchema() {
        Map<String, Object> schema = SchemaGenerator.generateSchema(Order.class);
        
        // 验证顶层结构
        assertEquals("object", schema.get("type"));
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertNotNull(props);
        assertTrue(props.containsKey("orderId"));
        assertTrue(props.containsKey("items"));
        assertTrue(props.containsKey("coupons"));
        assertTrue(props.containsKey("addresses"));
        
        // 验证 List<Item> 展开
        Map<String, Object> itemsSchema = (Map<String, Object>) props.get("items");
        assertEquals("array", itemsSchema.get("type"));
        Map<String, Object> itemSchema = (Map<String, Object>) itemsSchema.get("items");
        assertNotNull(itemSchema);
        assertEquals("object", itemSchema.get("type"));
        
        // 验证 Item 内的 Category 展开
        Map<String, Object> itemProps = (Map<String, Object>) itemSchema.get("properties");
        assertNotNull(itemProps);
        assertTrue(itemProps.containsKey("itemName"));
        assertTrue(itemProps.containsKey("price"));
        assertTrue(itemProps.containsKey("category"));
        assertTrue(itemProps.containsKey("tags"));
        
        // 验证 Category 展开
        Map<String, Object> categorySchema = (Map<String, Object>) itemProps.get("category");
        assertEquals("object", categorySchema.get("type"));
        Map<String, Object> catProps = (Map<String, Object>) categorySchema.get("properties");
        assertNotNull(catProps);
        assertTrue(catProps.containsKey("name"));
        assertTrue(catProps.containsKey("level"));
        
        // 验证 Set<String> 展开
        Map<String, Object> couponsSchema = (Map<String, Object>) props.get("coupons");
        assertEquals("array", couponsSchema.get("type"));
        assertEquals(true, couponsSchema.get("uniqueItems"));
        
        // 验证 Map<String, Address> 展开
        Map<String, Object> addressesSchema = (Map<String, Object>) props.get("addresses");
        assertEquals("object", addressesSchema.get("type"));
        Map<String, Object> addrSchema = (Map<String, Object>) addressesSchema.get("additionalProperties");
        assertNotNull(addrSchema);
        assertEquals("object", addrSchema.get("type"));
    }
}
