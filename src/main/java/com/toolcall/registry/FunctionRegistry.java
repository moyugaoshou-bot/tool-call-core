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
 * 支持自动检测参数类型
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
            
            // 自动检测参数信息
            String pName = detectParamName(p, ann);
            String type = detectParamType(p.getType());
            String description2 = detectParamDescription(p, ann);
            Object defaultVal = detectDefaultValue(p.getType(), ann);
            Map<String, Object> nestedSchema = detectNestedSchema(p.getType());
            
            params.put(pName, new FunctionDef.ParamSchema(
                type, description2, defaultVal, null, nestedSchema
            ));
        }
        
        method.setAccessible(true);
        functions.put(name, new FuncMeta(name, description, params, method, instance));
    }
    
    /**
     * 检测参数名称
     */
    private String detectParamName(Parameter p, Param ann) {
        if (ann != null && !ann.name().isEmpty()) {
            return ann.name();
        }
        return p.getName(); // 使用参数原名
    }
    
    /**
     * 检测参数类型
     */
    private String detectParamType(Class<?> paramType) {
        if (paramType == String.class) return "string";
        if (paramType == int.class || paramType == Integer.class) return "integer";
        if (paramType == long.class || paramType == Long.class) return "integer";
        if (paramType == double.class || paramType == Double.class) return "number";
        if (paramType == float.class || paramType == Float.class) return "number";
        if (paramType == boolean.class || paramType == Boolean.class) return "boolean";
        if (paramType.isArray()) return "array";
        if (List.class.isAssignableFrom(paramType)) return "array";
        if (Map.class.isAssignableFrom(paramType)) return "object";
        // 自定义类
        if (!paramType.isPrimitive() && paramType != Object.class) {
            return "object";
        }
        return "string";
    }
    
    /**
     * 检测参数描述
     */
    private String detectParamDescription(Parameter p, Param ann) {
        if (ann != null && !ann.description().isEmpty()) {
            return ann.description();
        }
        // 尝试从参数名生成描述
        String name = p.getName();
        return "The " + name + " parameter";
    }
    
    /**
     * 检测默认值
     */
    private Object detectDefaultValue(Class<?> paramType, Param ann) {
        if (ann != null && !ann.defaultValue().isEmpty()) {
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
        return null;
    }
    
    /**
     * 检测嵌套 Schema（复杂类型）
     */
    private Map<String, Object> detectNestedSchema(Class<?> paramType) {
        // 跳过基本类型和常见集合类型
        if (isBasicType(paramType) || Map.class.isAssignableFrom(paramType)) {
            return null;
        }
        if (List.class.isAssignableFrom(paramType) || paramType.isArray()) {
            return null;
        }
        // 对复杂对象生成 Schema
        if (!paramType.isPrimitive() && paramType != Object.class) {
            return SchemaGenerator.generateSchema(paramType);
        }
        return null;
    }
    
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
     * 获取所有函数定义
     */
    public List<FunctionDef> getAllFunctions() {
        return functions.values().stream()
            .map(f -> FunctionDef.of(f.name(), f.description(), f.parameters(), 
                getRequiredParams(f.parameters())))
            .toList();
    }
    
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
