# 使用指南

## 目录

1. [快速开始](#快速开始)
2. [工具定义](#工具定义)
3. [提示词生成](#提示词生成)
4. [解析和执行](#解析和执行)
5. [完整示例](#完整示例)

---

## 快速开始

### 1. 定义工具类

```java
public class WeatherTools {
    
    @Tool(name = "get_weather", description = "获取指定城市的天气信息")
    public String getWeather(
        @Param(name = "city", description = "城市名称", type = "string", required = true) String city,
        @Param(name = "unit", description = "温度单位", type = "string", required = false, defaultValue = "celsius") String unit
    ) {
        return "天气晴，" + city + "，25°C";
    }
    
    @Tool(name = "search_hotels", description = "搜索酒店")
    public List<Map<String, Object>> searchHotels(
        @Param(name = "city", description = "城市", type = "string", required = true) String city,
        @Param(name = "min_price", description = "最低价", type = "integer", required = false, defaultValue = "0") Integer minPrice
    ) {
        return List.of(Map.of("name", "Hotel A", "price", 500));
    }
}
```

### 2. 创建框架实例

```java
ToolCallFramework framework = new ToolCallFramework();
framework.register(new WeatherTools());
```

---

## 工具定义

### @Tool 注解

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| name | String | 方法名 | 工具名称 |
| description | String | "" | 工具描述 |

### @Param 注解

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| name | String | 参数名 | 参数名称 |
| description | String | "" | 参数描述 |
| type | String | "string" | 参数类型 |
| required | boolean | true | 是否必填 |
| defaultValue | String | "" | 默认值 |

### 支持的类型

- `string` - 字符串
- `integer` - 整数
- `number` - 数字
- `boolean` - 布尔值
- `array` - 数组
- `object` - 对象

---

## 提示词生成

### System Prompt 模式

```java
String prompt = framework.systemPrompt();
```

生成的 prompt 包含：
1. 工具定义（Markdown 格式）
2. 参数 JSON Schema
3. 输出规则（XML 标签格式）
4. Few-shot 示例

### API 模式

```java
String toolsJson = framework.toolsJson();
```

生成 OpenAI 格式的 tools JSON：

```json
{
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "get_weather",
        "description": "获取城市天气",
        "parameters": {
          "type": "object",
          "properties": {...},
          "required": ["city"]
        }
      }
    }
  ]
}
```

---

## 解析和执行

### 解析 LLM 返回

```java
// 解析返回结果
ParseResult result = framework.parse(llmResponse);

// 获取工具调用列表
List<ToolCall> toolCalls = result.toolCalls();

// 获取纯文本内容
String text = result.textContent();

// 获取思考过程（可选）
String thinking = result.thinking();
```

### 执行工具

```java
List<ToolResult> results = framework.execute(toolCalls);

// 检查执行结果
for (ToolResult result : results) {
    if (result.isSuccess()) {
        Object returnValue = result.result();
    } else {
        String error = result.error();
    }
}
```

---

## 完整示例

```java
public class AgentExample {
    public static void main(String[] args) {
        // 1. 创建框架并注册工具
        ToolCallFramework framework = new ToolCallFramework();
        framework.register(new WeatherTools());
        
        // 2. 获取提示词
        String systemPrompt = framework.systemPrompt();
        
        // 3. 构建 LLM 请求
        List<Message> messages = List.of(
            Message.system(systemPrompt),
            Message.user("北京天气怎么样？")
        );
        
        // 4. 调用 LLM
        String llmResponse = callLLM(messages);
        
        // 5. 解析响应
        ParseResult parsed = framework.parse(llmResponse);
        
        // 6. 执行工具
        if (parsed.hasToolCalls()) {
            List<ToolResult> results = framework.execute(parsed.toolCalls());
            
            // 7. 将结果返回给 LLM
            for (ToolResult tr : results) {
                messages.add(Message.tool(tr.id(), tr.result()));
            }
            
            // 8. 再次调用 LLM 获取最终回答
            String finalAnswer = callLLM(messages);
            System.out.println(finalAnswer);
        } else {
            System.out.println(parsed.textContent());
        }
        
        framework.shutdown();
    }
}
```

---

## 常见问题

### Q: 如何处理工具依赖？

框架支持 DAG 依赖执行。例如：

```java
ToolCall c1 = new ToolCall("call_001", "search_hotels", Map.of("city", "北京"));
ToolCall c2 = new ToolCall("call_002", "book_hotel", Map.of("hotel_name", "${call_001.result[0].name}"));

framework.execute(List.of(c1, c2));
```

### Q: 支持哪些 LLM 返回格式？

- `<tool_calls>...</tool_calls>` XML 标签
- `{ "tool_calls": [...] }` JSON 对象
- arguments 可以是字符串或对象
