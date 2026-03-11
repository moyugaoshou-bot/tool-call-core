# Tool Call Core 用户指南

本文档详细介绍如何使用 tool-call-core 框架实现 LLM Tool Calling 功能。

## 目录

1. [快速开始](#1-快速开始)
2. [工具注册](#2-工具注册)
3. [提示词生成](#3-提示词生成)
4. [LLM 响应解析](#4-llm-响应解析)
5. [工具调用执行](#5-工具调用执行)
6. [完整使用流程](#6-完整使用流程)
7. [高级特性](#7-高级特性)

---

## 1. 快速开始

### 1.1 添加依赖

```xml
<dependency>
    <groupId>com.toolcall</groupId>
    <artifactId>tool-call-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 1.2 最小示例

```java
// 1. 创建框架实例
ToolCallFramework framework = new ToolCallFramework();

// 2. 注册工具
framework.register(new Object() {
    @Tool(name = "hello", description = "打招呼")
    public String hello(String name) {
        return "Hello, " + name + "!";
    }
});

// 3. 获取提示词（给 LLM 用）
String prompt = framework.systemPrompt();

// 4. 模拟 LLM 返回
String llmResponse = """
    {"tool_calls":[{"id":"call_1","name":"hello","arguments":{"name":"World"}}]}
    """;

// 5. 解析 LLM 响应
ParseResult parseResult = framework.parse(llmResponse);

// 6. 执行工具调用
List<ToolResult> results = framework.execute(parseResult.toolCalls());

// 7. 获取结果
System.out.println(results.get(0).result());  // 输出: Hello, World!
```

---

## 2. 工具注册

### 2.1 基本用法

使用 `@Tool` 注解标记方法为可调用工具：

```java
public class WeatherTools {
    
    @Tool(name = "get_weather", description = "获取城市天气信息")
    public String getWeather(String city) {
        // 实现逻辑
        return "晴，25°C";
    }
}

// 注册到框架
framework.register(new WeatherTools());
```

### 2.2 @Tool 注解属性

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| name | String | 方法名 | 工具名称 |
| description | String | "" | 工具描述（用于提示词） |

```java
@Tool(name = "custom_name", description = "自定义工具描述")
public void myMethod() {}
```

### 2.3 参数配置

使用 `@Param` 注解配置参数：

```java
@Tool(name = "search", description = "搜索内容")
public List<String> search(
    @Param(
        name = "keyword",           // 参数名
        description = "搜索关键词",  // 描述
        required = true,            // 是否必需
        defaultValue = ""           // 默认值
    ) String keyword,
    
    @Param(name = "limit", defaultValue = "10") int limit
) {
    // 实现
}
```

**@Param 注解属性：**

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| name | String | 方法参数名 | 参数名称 |
| description | String | "" | 参数描述 |
| required | boolean | false | 是否必需 |
| defaultValue | String | "" | 默认值 |

### 2.4 自动类型检测

框架自动检测参数类型，无需手动指定：

```java
@Tool(name = "process", description = "处理数据")
public String process(
    String text,        // → string
    int count,         // → integer  
    double price,      // → number
    boolean active,    // → boolean
    List<String> tags, // → array
    Map<String, Object> meta // → object
) {
    return "ok";
}
```

**自动类型映射：**

| Java 类型 | JSON Schema 类型 |
|-----------|------------------|
| int, Integer | integer |
| long, Long | number |
| double, Double, float, Float | number |
| boolean, Boolean | boolean |
| String | string |
| List, 数组 | array |
| Map, 自定义对象 | object |

### 2.5 嵌套对象参数

支持嵌套 POJO 作为参数：

```java
// 定义嵌套类
public class User {
    public String name;
    public int age;
}

public class Contact {
    public String email;
    public Phone phone;
}

public class Phone {
    public String mobile;
}

// 注册工具
framework.register(new Object() {
    @Tool(name = "create_user", description = "创建用户")
    public String createUser(User user) {
        return user.name + ", " + user.age;
    }
});

// 调用时传入 Map
Map<String, Object> userMap = new HashMap<>();
userMap.put("name", "Alice");
userMap.put("age", 30);

ToolCall call = new ToolCall("c1", "create_user", Map.of("user", userMap));
```

---

## 3. 提示词生成

框架支持两种提示词模式：

### 3.1 API 模式（OpenAI 格式）

生成符合 OpenAI Function Calling 规范的 JSON：

```java
String toolsJson = framework.toolsJson();

// 输出格式：
/*
{
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "get_weather",
        "description": "获取天气",
        "parameters": {
          "type": "object",
          "properties": {
            "city": {
              "type": "string",
              "description": "城市名称"
            }
          },
          "required": ["city"]
        }
      }
    }
  ]
}
*/

// 使用方式：
// POST https://api.openai.com/v1/chat/completions
// Body: { "messages": [...], "tools": [toolsJson] }
```

### 3.2 Prompt 注入模式

生成自然语言提示词（可注入 system prompt）：

```java
// 完整提示词
String fullPrompt = framework.systemPrompt();

// 输出格式：
/*
# Tools

## Available Tools

### get_weather
**Description**: 获取城市天气

**Parameters**:
  - `city` (required): string - 城市名称

## JSON Schema
```json
{...}
```

## Output Format
<tool_calls>
[{"id": "...", "name": "...", "arguments": {...}}]
</tool_calls>
*/
```

### 3.3 单独获取各部分

```java
// 工具调用格式说明
String instructions = framework.callingInstructions();

// Few-shot 示例
String examples = framework.fewShotExamples();
```

---

## 4. LLM 响应解析

### 4.1 基本解析

框架自动从 LLM 返回中提取工具调用：

```java
// LLM 返回的原始响应
String llmResponse = """
    {"tool_calls":[{"id":"call_123","name":"get_weather","arguments":{"city":"北京"}}]}
    """;

// 解析
ParseResult result = framework.parse(llmResponse);

// 获取工具调用列表
List<ToolCall> calls = result.toolCalls();

for (ToolCall call : calls) {
    System.out.println("ID: " + call.id());           // call_123
    System.out.println("Name: " + call.name());       // get_weather
    System.out.println("Args: " + call.arguments());  // {city=北京}
}
```

### 4.2 支持的格式

**格式1：标准 OpenAI 格式**
```json
{"tool_calls":[{"id":"c1","name":"tool_name","arguments":{"param":"value"}}]}
```

**格式2：XML 标签格式**
```xml
<tool_calls>[{"id":"c1","name":"tool_name","arguments":{"param":"value"}}]</tool_calls>
```

**格式3：带 thinking 的格式**
```xml
<thinking>思考过程...</thinking>
<tool_calls>[...]</tool_calls>
```

### 4.3 解析结果

`ParseResult` 包含：

```java
ParseResult result = framework.parse(llmResponse);

result.toolCalls()    // List<ToolCall> 工具调用列表
result.thinking()     // String LLM 思考过程（如果有）
result.hasToolCalls() // boolean 是否有工具调用
```

---

## 5. 工具调用执行

### 5.1 基本执行

```java
// 从解析结果执行
List<ToolResult> results = framework.execute(parseResult.toolCalls());

// 或直接执行
List<ToolResult> results = framework.execute(llmResponse);
```

### 5.2 执行结果

`ToolResult` 包含：

```java
ToolResult r = results.get(0);

r.id()          // String 调用ID
r.name()        // String 工具名称
r.result()      // Object 执行结果
r.error()       // String 错误信息（如果有）
r.isSuccess()   // boolean 是否成功
r.durationMs()  // long 执行耗时(毫秒)
```

### 5.3 依赖执行（DAG）

框架自动解析工具间的依赖关系，按正确顺序执行：

```java
// 工具A的输出作为工具B的输入
// 格式：${toolId.field}

framework.register(new Object() {
    @Tool(name = "get_user", description = "获取用户")
    public Map<String, Object> getUser(String id) {
        return Map.of("id", id, "name", "Alice", "email", "alice@example.com");
    }
    
    @Tool(name = "send_email", description = "发送邮件")
    public String sendEmail(String to, String content) {
        return "Sent to " + to;
    }
});

// LLM 返回（使用依赖引用）
String llmResponse = """
    {
      "tool_calls": [
        {"id": "call_1", "name": "get_user", "arguments": {"id": "123"}},
        {"id": "call_2", "name": "send_email", "arguments": {"to": "${call_1.email}", "content": "Hello"}}
      ]
    }
    """;

// 框架自动按依赖顺序执行
List<ToolResult> results = framework.execute(llmResponse);
```

---

## 6. 完整使用流程

### 6.1 典型集成流程

```java
public class MyAssistant {
    private final ToolCallFramework framework;
    
    public MyAssistant() {
        this.framework = new ToolCallFramework();
        
        // 注册所有工具
        framework.register(new WeatherTools());
        framework.register(new SearchTools());
        framework.register(new UserTools());
    }
    
    // Step 1: 获取提示词（初始化时执行一次）
    public String getSystemPrompt() {
        return framework.systemPrompt();
    }
    
    // Step 2: 处理用户请求
    public String chat(String userMessage, String llmResponse) {
        // 解析 LLM 响应
        ParseResult parsed = framework.parse(llmResponse);
        
        if (!parsed.hasToolCalls()) {
            // LLM 无需调用工具，直接返回文本
            return llmResponse;
        }
        
        // 执行工具调用
        List<ToolResult> results = framework.execute(parsed.toolCalls());
        
        // 将结果格式化为 LLM 可处理的格式
        return formatToolResults(results);
    }
    
    private String formatToolResults(List<ToolResult> results) {
        // 格式化为 JSON 或文本
        return results.stream()
            .map(r -> r.isSuccess() 
                ? r.result().toString() 
                : "Error: " + r.error())
            .collect(Collectors.joining("\n"));
    }
}
```

### 6.2 与 LLM API 集成

```java
// 1. 准备请求
String toolsJson = framework.toolsJson();

HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
    .header("Authorization", "Bearer " + apiKey)
    .header("Content-Type", "application/json")
    .POST(HttpRequest.BodyPublishers.ofString("""
        {
          "model": "gpt-4",
          "messages": [
            {"role": "system", "content": "You have access to tools."},
            {"role": "user", "content": "What's the weather in Beijing?"}
          ],
          "tools": %s
        }
        """.formatted(toolsJson)))
    .build();

// 2. 发送请求并获取响应
HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
String llmResponse = response.body();

// 3. 解析并执行
ParseResult parsed = framework.parse(llmResponse);
List<ToolResult> results = framework.execute(parsed.toolCalls());
```

---

## 7. 高级特性

### 7.1 线程池配置

```java
// 使用默认线程数（CPU 核心数）
ToolCallFramework framework = new ToolCallFramework();

// 自定义线程池大小
ToolCallFramework framework = new ToolCallFramework(4);
```

### 7.2 关闭框架

```java
// 释放线程池资源
framework.shutdown();
```

### 7.3 工具元数据查询

```java
// 检查工具是否存在
boolean has = framework.registry().has("tool_name");

// 获取工具列表
Set<String> names = framework.registry().getNames();

// 获取工具元数据
FunctionRegistry.FuncMeta meta = framework.registry().get("tool_name");
```

---

## 常见问题

### Q: 参数名获取不到？

确保编译时添加 `-parameters` 参数：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <parameters>true</parameters>
    </configuration>
</plugin>
```

### Q: 匿名内部类无法注册？

框架已支持匿名内部类，但建议使用具体类：

```java
// 推荐
framework.register(new MyTools());

// 也支持（框架已修复）
framework.register(new Object() {
    @Tool(...) public void method() {}
});
```

### Q: 嵌套对象参数如何传递？

传入 `Map<String, Object>`：

```java
Map<String, Object> user = new HashMap<>();
user.put("name", "Alice");
user.put("age", 30);

ToolCall call = new ToolCall("c1", "create_user", Map.of("user", user));
```
