package com.toolcall.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.toolcall.annotation.Param;
import com.toolcall.model.ToolCall;
import com.toolcall.model.ToolResult;
import com.toolcall.registry.FunctionRegistry;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 工具执行器 - 负责执行工具调用
 * 
 * 核心职责：
 * 1. 参数转换：将 Map 参数转换为方法要求的类型
 * 2. 变量替换：支持 ${toolId.field} 引用前序结果
 * 3. 依赖解析：DAG 动态执行，支持并行
 * 4. 类型转换：使用 Jackson 将 Map 转换为 POJO
 * 
 * DAG 执行原理：
 * 1. 构建依赖图：taskId → 依赖的 taskId 集合
 * 2. 构建入度表：taskId → 依赖它的任务数量
 * 3. 入度为0的任务加入线程池
 * 4. 任务完成后，减少依赖它的任务的入度
 * 5. 入度变为0的任务加入线程池
 * 
 * 并发安全：
 * - 使用 ConcurrentHashMap 存储结果
 * - 使用 AtomicInteger 维护入度
 * - 使用 CountDownLatch 等待所有任务完成
 * 
 * 类结构关系：
 * ToolCallFramework
 *   └── ToolExecutor (工具执行)
 *         └── FunctionRegistry (获取方法元数据)
 */
public class ToolExecutor {
    
    private final FunctionRegistry registry;
    private final ObjectMapper mapper;
    private final ExecutorService executor;
    
    /**
     * 构造函数
     * @param registry 工具注册表
     * @param poolSize 线程池大小
     */
    public ToolExecutor(FunctionRegistry registry, int poolSize) {
        this.registry = registry;
        this.mapper = new ObjectMapper();
        // 使用固定大小线程池，防止无限制创建线程
        this.executor = Executors.newFixedThreadPool(poolSize);
    }
    
    /**
     * 默认构造函数（使用 CPU 核心数作为线程池大小）
     */
    public ToolExecutor(FunctionRegistry registry) {
        this(registry, Runtime.getRuntime().availableProcessors());
    }
    
    /**
     * 执行工具调用列表
     * 
     * 使用动态 DAG 执行算法：
     * 1. 构建依赖图：task → 依赖的 tasks
     * 2. 构建入度表：task → 依赖它的任务数
     * 3. 入度为0的任务加入执行队列
     * 4. 任务完成后，通知依赖它的任务入度-1
     * 5. 入度变为0的任务加入执行队列
     * 
     * @param calls 工具调用列表
     * @return 执行结果列表（顺序与输入一致）
     */
    public List<ToolResult> execute(List<ToolCall> calls) {
        if (calls == null || calls.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 构建调用映射
        Map<String, ToolCall> callMap = calls.stream()
            .collect(Collectors.toMap(ToolCall::id, c -> c));
        
        int taskCount = calls.size();
        
        // Step 1: 构建依赖图（谁依赖谁）
        // dependencyMap: taskId → 它依赖的 taskId 集合
        Map<String, Set<String>> dependencyMap = new HashMap<>();
        for (ToolCall c : calls) {
            dependencyMap.put(c.id(), extractDependencies(c.arguments()));
        }
        
        // Step 2: 构建入度表（有多少任务依赖我）
        // inDegree: taskId → 依赖它的任务数量
        Map<String, AtomicInteger> inDegree = new ConcurrentHashMap<>();
        
        // 初始化所有任务的入度为0
        for (ToolCall c : calls) {
            inDegree.put(c.id(), new AtomicInteger(0));
        }
        
        // 计算入度：遍历所有任务的依赖，对于依赖的task，被依赖的task入度+1
        for (Map.Entry<String, Set<String>> entry : dependencyMap.entrySet()) {
            String taskId = entry.getKey();
            Set<String> deps = entry.getValue();
            
            for (String depId : deps) {
                // depId 被 taskId 依赖，所以 taskId 的入度 +1
                // 实际上我们需要反向：taskId 依赖 depId，所以 depId 的入度 +1
                inDegree.computeIfAbsent(depId, k -> new AtomicInteger(0)).incrementAndGet();
            }
        }
        
        // Step 3: 存储结果
        Map<String, ToolResult> results = new ConcurrentHashMap<>();
        
        // Step 4: 追踪完成数量
        CountDownLatch latch = new CountDownLatch(taskCount);
        
        // 追踪正在执行的任务，防止重复提交
        Set<String> submitted = ConcurrentHashMap.newKeySet();
        
        // Step 5: 动态执行
        // 反复查找入度为0的任务提交执行，直到所有任务完成
        List<String> readyTasks = new ArrayList<>();
        
        // 初始：找出所有入度为0的任务（没有依赖的任务）
        for (Map.Entry<String, AtomicInteger> entry : inDegree.entrySet()) {
            if (entry.getValue().get() == 0) {
                readyTasks.add(entry.getKey());
            }
        }
        
        // 提交初始任务
        for (String taskId : readyTasks) {
            if (submitted.add(taskId)) {
                submitTask(taskId, callMap, inDegree, results, latch, submitted, dependencyMap);
            }
        }
        
        // 等待所有任务完成
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 按输入顺序返回结果
        return calls.stream()
            .map(c -> results.get(c.id()))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    /**
     * 提交任务到线程池
     * 任务完成后减少依赖任务的入度
     */
    private void submitTask(
            String taskId,
            Map<String, ToolCall> callMap,
            Map<String, AtomicInteger> inDegree,
            Map<String, ToolResult> results,
            CountDownLatch latch,
            Set<String> submitted,
            Map<String, Set<String>> dependencyMap) {
        
        executor.submit(() -> {
            try {
                // 执行任务
                ToolResult result = executeOne(callMap.get(taskId), results);
                results.put(taskId, result);
                
                // 任务完成后，减少依赖此任务的其他任务的入度
                // 找出哪些任务依赖于当前任务
                for (Map.Entry<String, Set<String>> entry : dependencyMap.entrySet()) {
                    String dependentTaskId = entry.getKey();
                    Set<String> deps = entry.getValue();
                    
                    if (deps.contains(taskId)) {
                        // 当前任务是 dependentTaskId 的依赖，入度-1
                        AtomicInteger degree = inDegree.get(dependentTaskId);
                        if (degree != null && degree.decrementAndGet() == 0) {
                            // 入度变为0，可以执行
                            if (submitted.add(dependentTaskId)) {
                                submitTask(dependentTaskId, callMap, inDegree, results, 
                                           latch, submitted, dependencyMap);
                            }
                        }
                    }
                }
                
            } finally {
                latch.countDown();
            }
        });
    }
    
    /**
     * 执行单个工具调用
     * 
     * 执行步骤：
     * 1. 变量替换（${toolId.field} → 实际值）
     * 2. 参数转换（Map → POJO）
     * 3. 反射调用方法
     */
    private ToolResult executeOne(ToolCall call, Map<String, ToolResult> context) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Step 1: 变量替换
            Map<String, Object> args = resolveVariables(call.arguments(), context);
            
            // Step 2: 获取工具元数据
            FunctionRegistry.FuncMeta meta = registry.get(call.name());
            if (meta == null) {
                return ToolResult.error(
                    call.id(), 
                    call.name(), 
                    "Unknown function: " + call.name(), 
                    System.currentTimeMillis() - startTime
                );
            }
            
            // Step 3: 参数类型转换
            Object[] params = convertParams(meta, args);
            
            // Step 4: 反射调用
            Object result = meta.method().invoke(meta.instance(), params);
            
            return ToolResult.success(
                call.id(), 
                call.name(), 
                result, 
                System.currentTimeMillis() - startTime
            );
            
        } catch (Exception e) {
            Throwable cause = e instanceof java.lang.reflect.InvocationTargetException 
                ? e.getCause() 
                : e;
            
            return ToolResult.error(
                call.id(), 
                call.name(), 
                cause.getMessage(), 
                System.currentTimeMillis() - startTime
            );
        }
    }
    
    /**
     * 提取依赖：从参数中查找 ${...} 引用
     * 
     * 例如：${user_tool.id} → 依赖 user_tool
     */
    private Set<String> extractDependencies(Map<String, Object> args) {
        Set<String> deps = new HashSet<>();
        Pattern pattern = Pattern.compile("\\$\\{([^}]+)\\}");
        
        for (Object value : args.values()) {
            if (value instanceof String str) {
                Matcher matcher = pattern.matcher(str);
                while (matcher.find()) {
                    String path = matcher.group(1);
                    String toolId = path.split("\\.")[0];
                    deps.add(toolId);
                }
            }
        }
        return deps;
    }
    
    /**
     * 解析变量：将 ${toolId.field} 替换为实际值
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveVariables(Map<String, Object> args, Map<String, ToolResult> context) {
        Map<String, Object> resolved = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            if (value instanceof String) {
                resolved.put(key, replaceVars((String) value, context));
            } else {
                resolved.put(key, value);
            }
        }
        
        return resolved;
    }
    
    /**
     * 替换字符串中的变量
     */
    private String replaceVars(String text, Map<String, ToolResult> context) {
        Pattern pattern = Pattern.compile("\\$\\{([^}]+)\\}");
        Matcher matcher = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        
        while (matcher.find()) {
            String path = matcher.group(1);
            String toolId = path.split("\\.")[0];
            String field = path.contains(".") ? path.split("\\.")[1] : null;
            
            ToolResult result = context.get(toolId);
            if (result != null && result.isSuccess()) {
                String replacement = "";
                if (field != null && result.result() instanceof Map) {
                    Object fieldValue = ((Map<String, Object>) result.result()).get(field);
                    replacement = fieldValue != null ? fieldValue.toString() : "";
                } else if (result.result() != null) {
                    replacement = result.result().toString();
                }
                matcher.appendReplacement(sb, replacement);
            }
        }
        
        matcher.appendTail(sb);
        return sb.toString();
    }
    
    /**
     * 转换参数：将 Map 转换为方法参数
     * 
     * 参数匹配优先级：
     * 1. @Param 注解指定的名称
     * 2. 方法参数名（需 -parameters 编译）
     * 3. 参数索引（按顺序匹配）
     */
    private Object[] convertParams(FunctionRegistry.FuncMeta meta, Map<String, Object> args) {
        Parameter[] parameters = meta.method().getParameters();
        Object[] result = new Object[parameters.length];
        
        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            Object value = null;
            
            // 1. 尝试用 @Param 注解的 name
            Param paramAnn = param.getAnnotation(Param.class);
            if (paramAnn != null && !paramAnn.name().isEmpty()) {
                value = args.get(paramAnn.name());
            }
            
            // 2. 如果没找到，尝试用方法参数名匹配
            if (value == null) {
                String paramName = param.getName();
                if (paramName != null) {
                    value = args.get(paramName);
                }
            }
            
            // 3. 如果还没找到，尝试用参数索引
            if (value == null) {
                List<Object> argValues = new ArrayList<>(args.values());
                if (i < argValues.size()) {
                    value = argValues.get(i);
                }
            }
            
            // 4. 字段名规范化（snake_case → camelCase）
            if (value instanceof Map) {
                value = normalizeFieldNames((Map<String, Object>) value);
            } else if (value instanceof List) {
                value = normalizeList((List<?>) value);
            }
            
            // 5. 类型转换
            if (value != null) {
                result[i] = mapper.convertValue(
                    value, 
                    TypeFactory.defaultInstance().constructType(param.getParameterizedType())
                );
            } else {
                result[i] = getDefaultValue(param.getType());
            }
        }
        
        return result;
    }
    
    /**
     * 字段名规范化：将 Map 中的 snake_case 键转换为 camelCase
     * 递归处理嵌套对象
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeFieldNames(Map<String, Object> input) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            // 转换键名
            String normalizedKey = snakeToCamel(key);
            
            // 递归处理嵌套 Map
            if (value instanceof Map) {
                result.put(normalizedKey, normalizeFieldNames((Map<String, Object>) value));
            } else if (value instanceof List) {
                result.put(normalizedKey, normalizeList((List<?>) value));
            } else {
                result.put(normalizedKey, value);
            }
        }
        return result;
    }
    
    @SuppressWarnings("unchecked")
    private List<Object> normalizeList(List<?> input) {
        List<Object> result = new ArrayList<>();
        for (Object item : input) {
            if (item instanceof Map) {
                result.add(normalizeFieldNames((Map<String, Object>) item));
            } else if (item instanceof List) {
                result.add(normalizeList((List<?>) item));
            } else {
                result.add(item);
            }
        }
        return result;
    }
    
    /**
     * snake_case 转 camelCase
     */
    private String snakeToCamel(String str) {
        if (str == null || !str.contains("_")) {
            return str;
        }
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = false;
        for (char c : str.toCharArray()) {
            if (c == '_') {
                nextUpper = true;
            } else if (nextUpper) {
                sb.append(Character.toUpperCase(c));
                nextUpper = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
    
    /**
     * 获取基本类型的默认值
     */
    private Object getDefaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0;
        if (type == float.class) return 0.0f;
        return null;
    }
    
    /**
     * 关闭执行器
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
