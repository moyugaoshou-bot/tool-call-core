package com.toolcall.registry;

import com.toolcall.annotation.Param;
import com.toolcall.annotation.Tool;
import com.toolcall.model.FunctionDef;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册表
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
        List<String> required = new ArrayList<>();
        
        for (Parameter p : method.getParameters()) {
            Param ann = p.getAnnotation(Param.class);
            if (ann != null) {
                String pName = ann.name().isEmpty() ? p.getName() : ann.name();
                Object defaultVal = ann.defaultValue().isEmpty() ? null : parseDefault(ann);
                
                params.put(pName, new FunctionDef.ParamSchema(
                    ann.type(), ann.description(), defaultVal, null
                ));
                if (ann.required()) required.add(pName);
            }
        }
        
        method.setAccessible(true);
        functions.put(name, new FuncMeta(name, description, params, method, instance));
    }
    
    private Object parseDefault(Param ann) {
        if (ann.defaultValue().isEmpty()) return null;
        return switch (ann.type()) {
            case "integer" -> Integer.parseInt(ann.defaultValue());
            case "number", "double" -> Double.parseDouble(ann.defaultValue());
            case "boolean" -> Boolean.parseBoolean(ann.defaultValue());
            default -> ann.defaultValue();
        };
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
            .filter(e -> e.getValue().defaultValue() == null)  // 没有默认值的才是必填
            .map(Map.Entry::getKey)
            .toList();
    }
    
    public FuncMeta get(String name) { return functions.get(name); }
    public boolean has(String name) { return functions.containsKey(name); }
    public Set<String> getNames() { return Collections.unmodifiableSet(functions.keySet()); }
}
