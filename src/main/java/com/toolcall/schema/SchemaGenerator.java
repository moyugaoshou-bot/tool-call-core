package com.toolcall.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.*;
import java.util.*;

/**
 * Java Bean Schema 转换器
 * 将 Java 类转换为 JSON Schema
 * 参考 langchain4j 和 spring-ai 的实现
 */
public class SchemaGenerator {
    
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    /**
     * 从 Java 类生成 JSON Schema
     */
    public static Map<String, Object> generateSchema(Class<?> clazz) {
        return generateSchema(clazz, new HashSet<>());
    }
    
    private static Map<String, Object> generateSchema(Class<?> clazz, Set<Class<?>> visited) {
        // 避免循环引用
        if (visited.contains(clazz)) {
            return Map.of("type", "object");
        }
        visited.add(clazz);
        
        Map<String, Object> schema = new LinkedHashMap<>();
        
        // 基本类型处理
        if (isBasicType(clazz)) {
            schema.put("type", getJsonType(clazz));
            return schema;
        }
        
        // 数组/列表类型
        if (clazz.isArray()) {
            return Map.of("type", "array", "items", generateSchema(clazz.getComponentType(), visited));
        }
        
        if (List.class.isAssignableFrom(clazz)) {
            // 获取泛型类型
            return Map.of("type", "array", "items", Map.of("type", "object"));
        }
        
        // Map 类型
        if (Map.class.isAssignableFrom(clazz)) {
            return Map.of("type", "object");
        }
        
        // 对象类型 - 遍历字段
        schema.put("type", "object");
        schema.put("properties", getProperties(clazz, visited));
        schema.put("required", getRequiredFields(clazz));
        
        return schema;
    }
    
    /**
     * 获取类的属性
     */
    private static Map<String, Object> getProperties(Class<?> clazz, Set<Class<?>> visited) {
        Map<String, Object> properties = new LinkedHashMap<>();
        
        for (Field field : clazz.getDeclaredFields()) {
            // 跳过静态字段和 serialVersionUID
            if (Modifier.isStatic(field.getModifiers())) continue;
            
            String fieldName = field.getName();
            Class<?> fieldType = field.getType();
            
            // 获取字段的 getter 方法上的注释作为描述
            String description = getFieldDescription(field);
            
            Map<String, Object> propSchema = generateSchema(fieldType, visited);
            if (!description.isEmpty()) {
                propSchema.put("description", description);
            }
            
            properties.put(fieldName, propSchema);
        }
        
        return properties;
    }
    
    /**
     * 获取必填字段
     */
    private static List<String> getRequiredFields(Class<?> clazz) {
        List<String> required = new ArrayList<>();
        
        for (Field field : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            
            // 没有默认值的就是必填的
            if (!hasDefaultValue(field)) {
                required.add(field.getName());
            }
        }
        
        return required;
    }
    
    /**
     * 检查字段是否有默认值
     */
    private static boolean hasDefaultValue(Field field) {
        // 检查字段类型的基本类型默认值
        Class<?> type = field.getType();
        if (type == boolean.class) return false;
        if (type == int.class || type == long.class || type == double.class) return false;
        return true;
    }
    
    /**
     * 获取字段描述（从 getter 方法的注释获取）
     */
    private static String getFieldDescription(Field field) {
        try {
            Method getter = findGetter(field);
            if (getter != null && getter.isAnnotationPresent(SchemaDescription.class)) {
                return getter.getAnnotation(SchemaDescription.class).value();
            }
        } catch (Exception e) {
            // ignore
        }
        return "";
    }
    
    /**
     * 查找 getter 方法
     */
    private static Method findGetter(Field field) {
        String getterName = "get" + field.getName().substring(0, 1).toUpperCase() + field.getName().substring(1);
        try {
            return field.getDeclaringClass().getMethod(getterName);
        } catch (NoSuchMethodException e) {
            // 尝试 boolean 的 is getter
            if (field.getType() == boolean.class) {
                try {
                    return field.getDeclaringClass().getMethod("is" + field.getName().substring(0, 1).toUpperCase() + field.getName().substring(1));
                } catch (NoSuchMethodException ex) {
                    return null;
                }
            }
            return null;
        }
    }
    
    /**
     * 判断是否为基础类型
     */
    private static boolean isBasicType(Class<?> clazz) {
        return clazz == String.class ||
               clazz == Integer.class || clazz == int.class ||
               clazz == Long.class || clazz == long.class ||
               clazz == Double.class || clazz == double.class ||
               clazz == Float.class || clazz == float.class ||
               clazz == Boolean.class || clazz == boolean.class ||
               clazz == Object.class;
    }
    
    /**
     * 获取 JSON 类型
     */
    private static String getJsonType(Class<?> clazz) {
        if (clazz == String.class) return "string";
        if (clazz == Integer.class || clazz == int.class ||
            clazz == Long.class || clazz == long.class ||
            clazz == Short.class || clazz == short.class) return "integer";
        if (clazz == Double.class || clazz == double.class ||
            clazz == Float.class || clazz == float.class) return "number";
        if (clazz == Boolean.class || clazz == boolean.class) return "boolean";
        return "object";
    }
    
    /**
     * 将 Map 转换为指定类型的对象
     */
    public static <T> T convertValue(Object from, Class<T> toClass) {
        if (from == null) return null;
        return MAPPER.convertValue(from, toClass);
    }
}
