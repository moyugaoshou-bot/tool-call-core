package com.toolcall.schema;

import java.lang.reflect.*;
import java.util.*;

/**
 * Java Bean Schema 转换器 - 简化版
 */
public class SchemaGenerator {
    
    public static Map<String, Object> generateSchema(Class<?> clazz) {
        if (clazz == null) {
            return Map.of("type", "object");
        }
        
        // 基本类型
        if (isBasicType(clazz)) {
            return Map.of("type", getJsonType(clazz));
        }
        
        // 数组
        if (clazz.isArray()) {
            return Map.of("type", "array");
        }
        
        // List
        if (List.class.isAssignableFrom(clazz)) {
            return Map.of("type", "array");
        }
        
        // Map
        if (Map.class.isAssignableFrom(clazz)) {
            return Map.of("type", "object");
        }
        
        // 对象 - 展开字段
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        
        for (Field field : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            
            String name = field.getName();
            Class<?> type = field.getType();
            
            properties.put(name, Map.of("type", getJsonType(type)));
            
            // primitive 类型默认值为 0/false，非必需
            if (!isOptionalType(type)) {
                required.add(name);
            }
        }
        
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        
        return schema;
    }
    
    private static boolean isBasicType(Class<?> type) {
        return type == String.class ||
               type == Integer.class || type == int.class ||
               type == Long.class || type == long.class ||
               type == Double.class || type == double.class ||
               type == Float.class || type == float.class ||
               type == Boolean.class || type == boolean.class ||
               type == Object.class;
    }
    
    private static String getJsonType(Class<?> type) {
        if (type == String.class) return "string";
        if (type == Integer.class || type == int.class ||
            type == Long.class || type == long.class ||
            type == Short.class || type == short.class) return "integer";
        if (type == Double.class || type == double.class ||
            type == Float.class || type == float.class) return "number";
        if (type == Boolean.class || type == boolean.class) return "boolean";
        return "object";
    }
    
    private static boolean isOptionalType(Class<?> type) {
        return type == String.class ||
               type == Integer.class || type == Long.class ||
               type == Double.class || type == Float.class ||
               type == Boolean.class || type == Object.class;
    }
}
