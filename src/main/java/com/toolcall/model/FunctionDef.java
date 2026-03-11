package com.toolcall.model;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工具定义 - 完全符合 OpenAI Function Calling 格式规范
 * 
 * OpenAI 格式参考:
 * https://platform.openai.com/docs/guides/function-calling
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
     * 参数 schema - 符合 JSON Schema 规范
     * 注意: defaultValue 和 enumValues 是运行时信息，不应该发送给 LLM
     */
    public record ParamSchema(
        String type,
        String description,
        Object defaultValue,    // 运行时默认值，不发送给 LLM
        List<String> enumValues // 运行时枚举值，不发送给 LLM
    ) {
        // 工厂方法
        public static ParamSchema string(String description) { 
            return new ParamSchema("string", description, null, null); 
        }
        
        public static ParamSchema integer(String description) { 
            return new ParamSchema("integer", description, null, null); 
        }
        
        public static ParamSchema number(String description) { 
            return new ParamSchema("number", description, null, null); 
        }
        
        public static ParamSchema boolean_(String description) { 
            return new ParamSchema("boolean", description, null, null); 
        }
        
        public static ParamSchema array(String description) { 
            return new ParamSchema("array", description, null, null); 
        }
        
        public static ParamSchema object(String description) { 
            return new ParamSchema("object", description, null, null); 
        }
        
        /** 枚举类型参数 */
        public static ParamSchema withEnum(String description, List<String> enumVals) { 
            return new ParamSchema("string", description, null, enumVals); 
        }
        
        /**
         * 转换为 JSON Schema（不包含运行时信息）
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
            return schema;
        }
    }
    
    /**
     * 生成 JSON Schema（用于 API 参数）
     * 只包含 type, description, properties, required
     * 不包含运行时信息
     */
    public Map<String, Object> toJsonSchema() {
        var schema = new java.util.LinkedHashMap<String, Object>();
        schema.put("type", "object");
        
        // 转换 properties，过滤掉运行时信息
        var props = new java.util.LinkedHashMap<String, Object>();
        for (var entry : parameters.properties().entrySet()) {
            props.put(entry.getKey(), entry.getValue().toJsonSchema());
        }
        schema.put("properties", props);
        
        // 只包含真正必填的参数
        schema.put("required", parameters.required());
        
        return schema;
    }
}
