# API 文档

## ToolCallFramework

主入口类

### 构造函数

```java
// 默认构造
new ToolCallFramework()

// 指定线程池大小
new ToolCallFramework(int poolSize)
```

### 方法

#### register

注册工具对象

```java
ToolCallFramework register(Object instance)
```

扫描对象中的 `@Tool` 注解方法并注册。

**参数:** `instance` - 包含 `@Tool` 注解方法的对象

**返回:** 框架实例（链式调用）

---

#### registry

获取工具注册表

```java
FunctionRegistry registry()
```

---

#### systemPrompt

生成 system prompt

```java
String systemPrompt()
```

生成包含工具定义、输出规则、Few-shot 示例的完整 prompt。

**返回:** prompt 字符串

---

#### toolsJson

生成 OpenAI 格式的 tools JSON

```java
String toolsJson()
```

**返回:** JSON 格式的工具定义

---

#### parse

解析 LLM 返回

```java
ParseResult parse(String llmResponse)
```

**参数:** `llmResponse` - LLM 返回的文本

**返回:** `ParseResult` 包含文本内容、工具调用列表、思考过程

---

#### parseToolCalls

解析并提取工具调用

```java
List<ToolCall> parseToolCalls(String llmResponse)
```

---

#### execute

执行工具调用

```java
List<ToolResult> execute(List<ToolCall> calls)
List<ToolResult> execute(String llmResponse)  // 一键执行
```

---

#### shutdown

关闭执行器

```java
void shutdown()
```

---

## ParseResult

解析结果

### 字段

```java
String textContent      // 清理后的文本
List<ToolCall> toolCalls // 工具调用列表
String thinking       // 思考过程（可选）
```

### 方法

```java
boolean hasToolCalls() // 是否包含工具调用
```

---

## ToolCall

工具调用

### 字段

```java
String id           // 调用 ID
String name        // 工具名称
Map<String, Object> arguments // 参数
```

---

## ToolResult

工具执行结果

### 字段

```java
String id        // 调用 ID
String name     // 工具名称
Object result   // 返回值
String error    // 错误信息
long durationMs // 执行耗时
```

### 方法

```java
boolean isSuccess() // 是否成功
```

---

## @Tool

标记方法为工具

### 属性

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| name | String | 方法名 | 工具名称 |
| description | String | "" | 工具描述 |

---

## @Param

标记方法参数

### 属性

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| name | String | 参数名 | 参数名称 |
| description | String | "" | 参数描述 |
| type | String | "string" | 参数类型 |
| required | boolean | true | 是否必填 |
| defaultValue | String | "" | 默认值 |

---

## FunctionRegistry

工具注册表

### 方法

```java
boolean has(String name)    // 检查工具是否存在
FuncMeta get(String name)   // 获取工具元数据
Set<String> getNames()    // 获取所有工具名称
```

---

## PromptGenerator

提示词生成器

### 方法

```java
String toSystemPrompt()              // 完整 prompt
String toOpenAIToolsJson()          // OpenAI 格式 JSON
String getOutputRules()             // 输出规则
String getFewShotExamples()        // Few-shot 示例
```

---

## ToolCallParser

工具调用解析器

### 方法

```java
ParseResult parse(String response)  // 解析 LLM 响应
boolean hasToolCalls(String response) // 是否包含工具调用
```

---

## ToolExecutor

工具执行器

### 构造函数

```java
new ToolExecutor(FunctionRegistry registry)
new ToolExecutor(FunctionRegistry registry, int poolSize)
```

### 方法

```java
List<ToolResult> execute(List<ToolCall> calls)  // 执行工具调用列表
void shutdown()  // 关闭执行器
```
