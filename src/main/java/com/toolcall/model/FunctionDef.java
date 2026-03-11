package com.toolcall.model;

import java.util.List;
import java.util.Map;

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
     */
    public record ParamSchema(
        String type,
        String description,
        Object defaultValue,
        List<String> enumValues
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
    }
}
