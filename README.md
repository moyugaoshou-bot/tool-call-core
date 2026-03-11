# Tool Call Core

LLM Tool Calling Framework - 零依赖 AI 框架的纯 Java 实现

## 核心特性

- **注解方式注册工具** - 使用 `@Tool` 和 `@Param` 注解
- **两种模式支持** - API 模式和 Prompt 模式
- **OpenAI 格式兼容** - 生成和解析符合 OpenAI 规范的 JSON
- **XML 标签解析** - 支持 `<tool_calls>` 标签格式
- **DAG 依赖执行** - 支持工具调用依赖关系
- **参数自动转换** - 自动类型转换和默认值处理

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.toolcall</groupId>
    <artifactId>tool-call-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. 定义工具

```java
public class MyTools {
    
    @Tool(name = "get_weather", description = "获取城市天气")
    public String getWeather(
        @Param(name = "city", description = "城市名称", type = "string", required = true) String city,
        @Param(name = "unit", description = "温度单位", type = "string", required = false, defaultValue = "celsius") String unit
    ) {
        return "天气晴，" + city + "，25°C";
    }
}
```

### 3. 使用框架

```java
ToolCallFramework framework = new ToolCallFramework();
framework.register(new MyTools());

// 获取提示词
String prompt = framework.systemPrompt();

// 解析 LLM 返回
ParseResult result = framework.parse(llmResponse);

// 执行工具
List<ToolResult> results = framework.execute(result.toolCalls());
```

## 两种模式

### API 模式

通过 LLM API 的 `tools` 参数传入工具定义：

```java
String toolsJson = framework.toolsJson();
// POST to LLM API with tools parameter
```

### Prompt 模式

将工具信息注入 system prompt：

```java
String prompt = framework.systemPrompt();
// Inject into system message
```

## 项目结构

```
tool-call-core/
├── src/main/java/com/toolcall/
│   ├── ToolCallFramework.java    # 主入口
│   ├── annotation/              # @Tool, @Param 注解
│   ├── generator/              # 提示词生成器
│   ├── parser/                # 解析器
│   ├── executor/              # 执行器
│   ├── registry/              # 注册表
│   └── model/                 # 数据模型
└── docs/                      # 文档
```

## License

MIT
