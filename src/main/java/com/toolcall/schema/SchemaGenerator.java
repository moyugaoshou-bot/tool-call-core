package com.toolcall.schema;

import java.lang.reflect.*;
import java.util.*;

/**
 * Java Bean Schema 转换器 - 支持嵌套POJO和泛型递归展开
 */
public class SchemaGenerator {
    
    public static Map<String, Object> generateSchema(Class<?> clazz) {
        return generateSchema(clazz, null, new HashSet<>());
    }
    
    private static Map<String, Object> generateSchema(Class<?> clazz, Type genericType, Set<Class<?>> visited) {
        if (clazz == null) {
            return Map.of("type", "object");
        }
        
        // 防止循环引用
        if (visited.contains(clazz)) {
            return Map.of("type", "object");
        }
        
        // 基本类型
        if (isBasicType(clazz)) {
            return Map.of("type", getJsonType(clazz));
        }
        
        // 数组 - 获取元素类型
        if (clazz.isArray()) {
            Class<?> componentType = clazz.getComponentType();
            Map<String, Object> itemSchema = generateSchema(componentType, null, new HashSet<>(visited));
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "array");
            schema.put("items", itemSchema);
            return schema;
        }
        
        // List - 获取泛型参数
        if (List.class.isAssignableFrom(clazz)) {
            Class<?> itemType = Object.class;
            if (genericType instanceof ParameterizedType pt) {
                Type[] args = pt.getActualTypeArguments();
                if (args.length > 0 && args[0] instanceof Class) {
                    itemType = (Class<?>) args[0];
                }
            }
            Map<String, Object> itemSchema = generateSchema(itemType, null, new HashSet<>(visited));
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "array");
            schema.put("items", itemSchema);
            return schema;
        }
        
        // Set - 类似List处理
        if (Set.class.isAssignableFrom(clazz)) {
            Class<?> itemType = Object.class;
            if (genericType instanceof ParameterizedType pt) {
                Type[] args = pt.getActualTypeArguments();
                if (args.length > 0 && args[0] instanceof Class) {
                    itemType = (Class<?>) args[0];
                }
            }
            Map<String, Object> itemSchema = generateSchema(itemType, null, new HashSet<>(visited));
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "array");
            schema.put("uniqueItems", true);
            schema.put("items", itemSchema);
            return schema;
        }
        
        // Map - 简化处理，value类型展开
        if (Map.class.isAssignableFrom(clazz)) {
            Class<?> valueType = Object.class;
            if (genericType instanceof ParameterizedType pt) {
                Type[] args = pt.getActualTypeArguments();
                if (args.length > 1 && args[1] instanceof Class) {
                    valueType = (Class<?>) args[1];
                }
            }
            Map<String, Object> valueSchema = generateSchema(valueType, null, new HashSet<>(visited));
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("additionalProperties", valueSchema);
            return schema;
        }
        
        // 标记为已访问
        visited.add(clazz);
        
        // 对象 - 递归展开字段
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        
        for (Field field : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            
            String name = field.getName();
            Class<?> type = field.getType();
            Type fieldGenericType = field.getGenericType();
            
            // 递归生成嵌套对象的schema，传递泛型信息
            Map<String, Object> fieldSchema = generateSchema(type, fieldGenericType, new HashSet<>(visited));
            properties.put(name, fieldSchema);
            
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
