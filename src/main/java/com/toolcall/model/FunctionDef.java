package com.toolcall.model;

import java.util.List;
import java.util.Map;

/**
 * 工具定义 - 对应 OpenAI function calling 格式
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

    public record ParamSchema(
        String type,
        String description,
        Object defaultValue,
        List<String> enumValues
    ) {
        public static ParamSchema string(String desc) { return new ParamSchema("string", desc, null, null); }
        public static ParamSchema integer(String desc) { return new ParamSchema("integer", desc, null, null); }
        public static ParamSchema number(String desc) { return new ParamSchema("number", desc, null, null); }
        public static ParamSchema boolean_(String desc) { return new ParamSchema("boolean", desc, null, null); }
        public static ParamSchema array(String desc) { return new ParamSchema("array", desc, null, null); }
        public static ParamSchema object(String desc) { return new ParamSchema("object", desc, null, null); }
        public static ParamSchema withEnum(String desc, List<String> enumVals) { 
            return new ParamSchema("string", desc, null, enumVals); 
        }
        public static ParamSchema optional(String desc, Object defaultVal) { 
            return new ParamSchema(inferType(desc), desc, defaultVal, null); 
        }
        private static String inferType(Object val) {
            if (val instanceof Integer) return "integer";
            if (val instanceof Number) return "number";
            if (val instanceof Boolean) return "boolean";
            return "string";
        }
    }
}
