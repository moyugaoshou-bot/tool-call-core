package com.toolcall.registry;

import com.toolcall.annotation.Param;
import com.toolcall.annotation.Tool;
import com.toolcall.model.FunctionDef;
import com.toolcall.schema.SchemaGenerator;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册表
 * 支持自动检测复杂类型并生成 JSON Schema
 */
public class FunctionRegistry {
    private final Map<String, FuncMeta> functions = new ConcurrentHashMap<>();
    
    public record FuncMeta(
        String name,
        String description,
        Map<String, FunctionDef.ParamSchema> parameters,
        Method method,
        Object instance
    ) {}
    
    /**
     * 注册工具对象（扫描 @Tool 注解）
     */
    public FunctionRegistry register(Object instance) {
        for (Method method : instance.getClass().getDeclaredMethods()) {
            Tool tool = method.getAnnotation(Tool.class);
            if (tool != null) {
                registerMethod(instance, method, tool);
            }
        }
        return this;
    }
    
    private void registerMethod(Object instance, Method method, Tool tool) {
        String name = tool.name().isEmpty() ? method.getName() : tool.name();
        String description = tool.description();
        
        Map<String, FunctionDef.ParamSchema> params = new LinkedHashMap<>();
        
        for (Parameter p : method.getParameters()) {
            Param ann = p.getAnnotation(Param.class);
            if (ann != null) {
                String pName = ann.name().isEmpty() ? p.getName() : ann.name();
                
                // 自动检测类型
                String type = detectType(p.getType(), ann);
                Object defaultVal = parseDefaultValue(p.getType(), ann);
                
                // 如果是复杂类型（自定义类），生成 JSON Schema
                Map<String, Object> schema = null;
                if (isComplexType(p.getType()) && !ann.type().equals("array")) {
                    schema = SchemaGenerator.generateSchema(p.getType());
                }
                
                params.put(pName, new FunctionDef.ParamSchema(
                    type, ann.description(), defaultVal, null, schema
                ));
            }
        }
        
        method.setAccessible(true);
        functions.put(name, new FuncMeta(name, description, params, method, instance));
    }
    
    /**
     * 检测参数类型
     */
    private String detectType(Class<?> paramType, Param ann) {
        // 如果用户明确指定了类型，使用用户指定的
        if (!ann.type().isEmpty() && !ann.type().equals("auto")) {
            return ann.type();
        }
        
        // 自动检测
        if (paramType == String.class) return "string";
        if (paramType == int.class || paramType == Integer.class) return "integer";
        if (paramType == long.class || paramType == Long.class) return "integer";
        if (paramType == double.class || paramType == Double.class) return "number";
        if (paramType == float.class || paramType == Float.class) return "number";
        if (paramType == boolean.class || paramType == Boolean.class) return "boolean";
        if (paramType.isArray()) return "array";
        if (List.class.isAssignableFrom(paramType)) return "array";
        if (Map.class.isAssignableFrom(paramType)) return "object";
        
        // 自定义类视为 object
        if (!paramType.isPrimitive() && paramType != Object.class) {
            return "object";
        }
        
        return "string";
    }
    
    /**
     * 判断是否为复杂类型
     */
    private boolean isComplexType(Class<?> type) {
        return !isBasicType(type);
    }
    
    /**
     * 判断是否为基础类型
     */
    private boolean isBasicType(Class<?> type) {
        return type == String.class ||
               type == Integer.class || type == int.class ||
               type == Long.class || type == long.class ||
               type == Double.class || type == double.class ||
               type == Float.class || type == float.class ||
               type == Boolean.class || type == boolean.class ||
               type == Object.class;
    }
    
    /**
     * 解析默认值
     */
    private Object parseDefaultValue(Class<?> paramType, Param ann) {
        if (ann.defaultValue().isEmpty()) return null;
        
        try {
            if (paramType == Integer.class || paramType == int.class) {
                return Integer.parseInt(ann.defaultValue());
            }
            if (paramType == Long.class || paramType == long.class) {
                return Long.parseLong(ann.defaultValue());
            }
            if (paramType == Double.class || paramType == double.class) {
                return Double.parseDouble(ann.defaultValue());
            }
            if (paramType == Boolean.class || paramType == boolean.class) {
                return Boolean.parseBoolean(ann.defaultValue());
            }
            return ann.defaultValue();
        } catch (NumberFormatException e) {
            return ann.defaultValue();
        }
    }
    
    /**
     * 获取所有函数定义
     */
    public List<FunctionDef> getAllFunctions() {
        return functions.values().stream()
            .map(f -> FunctionDef.of(f.name(), f.description(), f.parameters(), 
                getRequiredParams(f.parameters())))
            .toList();
    }
    
    /**
     * 获取必填参数列表
     */
    private List<String> getRequiredParams(Map<String, FunctionDef.ParamSchema> params) {
        return params.entrySet().stream()
            .filter(e -> e.getValue().defaultValue() == null)
            .map(Map.Entry::getKey)
            .toList();
    }
    
    public FuncMeta get(String name) { return functions.get(name); }
    public boolean has(String name) { return functions.containsKey(name); }
    public Set<String> getNames() { return Collections.unmodifiableSet(functions.keySet()); }
}
