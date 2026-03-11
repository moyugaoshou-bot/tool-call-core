package com.toolcall.model;

import java.util.List;
import java.util.Map;

/**
 * 工具定义 - 完全符合 OpenAI Function Calling 格式规范
 */
public record FunctionDef(
    String name,
    String description,
    Parameters parameters
) {
    public static FunctionDef of(String name, String description, Map<String, ParamSchema> props, List<String> required) {
        return new FunctionDef(name, description, new Parameters("object", props, required));
    }

    public record Parameters(
        String type,
        Map<String, ParamSchema> properties,
        List<String> required
    ) {}

    /**
     * 参数 schema - 支持嵌套结构
     */
    public record ParamSchema(
        String type,
        String description,
        Object defaultValue,
        List<String> enumValues,
        Map<String, Object> nestedProperties  // 嵌套对象属性
    ) {
        // 工厂方法
        public static ParamSchema string(String description) { 
            return new ParamSchema("string", description, null, null, null); 
        }
        
        public static ParamSchema integer(String description) { 
            return new ParamSchema("integer", description, null, null, null); 
        }
        
        public static ParamSchema number(String description) { 
            return new ParamSchema("number", description, null, null, null); 
        }
        
        public static ParamSchema boolean_(String description) { 
            return new ParamSchema("boolean", description, null, null, null); 
        }
        
        public static ParamSchema array(String description) { 
            return new ParamSchema("array", description, null, null, null); 
        }
        
        public static ParamSchema object(String description) { 
            return new ParamSchema("object", description, null, null, null); 
        }
        
        /** 枚举类型参数 */
        public static ParamSchema withEnum(String description, List<String> enumVals) { 
            return new ParamSchema("string", description, null, enumVals, null); 
        }
        
        /**
         * 转换为 JSON Schema
         */
        public Map<String, Object> toJsonSchema() {
            var schema = new java.util.LinkedHashMap<String, Object>();
            schema.put("type", type);
            
            if (description != null && !description.isEmpty()) {
                schema.put("description", description);
            }
            
            if (enumValues != null && !enumValues.isEmpty()) {
                schema.put("enum", enumValues);
            }
            
            // 如果有嵌套属性
            if (nestedProperties != null && !nestedProperties.isEmpty()) {
                schema.put("properties", nestedProperties);
            }
            
            return schema;
        }
    }
    
    /**
     * 生成 JSON Schema
     */
    public Map<String, Object> toJsonSchema() {
        var schema = new java.util.LinkedHashMap<String, Object>();
        schema.put("type", "object");
        
        var props = new java.util.LinkedHashMap<String, Object>();
        for (var entry : parameters.properties().entrySet()) {
            props.put(entry.getKey(), entry.getValue().toJsonSchema());
        }
        schema.put("properties", props);
        schema.put("required", parameters.required());
        
        return schema;
    }
}
