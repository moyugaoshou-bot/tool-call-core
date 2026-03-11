package com.toolcall.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.toolcall.model.ToolCall;
import com.toolcall.model.ToolResult;
import com.toolcall.registry.FunctionRegistry;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.*;

/**
 * 工具执行器 - 支持 DAG 依赖执行
 */
public class ToolExecutor {
    
    private final FunctionRegistry registry;
    private final ObjectMapper mapper;
    private final ExecutorService executor;
    
    public ToolExecutor(FunctionRegistry registry, int poolSize) {
        this.registry = registry;
        this.mapper = new ObjectMapper();
        this.executor = Executors.newFixedThreadPool(poolSize);
    }
    
    public ToolExecutor(FunctionRegistry registry) {
        this(registry, Runtime.getRuntime().availableProcessors());
    }
    
    /**
     * 执行工具调用列表
     */
    public List<ToolResult> execute(List<ToolCall> calls) {
        if (calls == null || calls.isEmpty()) return Collections.emptyList();
        
        // 构建依赖图
        Map<String, ToolCall> callMap = calls.stream()
            .collect(java.util.stream.Collectors.toMap(ToolCall::id, c -> c));
        
        Map<String, Set<String>> deps = new HashMap<>();
        for (ToolCall c : calls) {
            deps.put(c.id(), extractDependencies(c.arguments()));
        }
        
        // 拓扑排序
        Map<String, ToolResult> results = new ConcurrentHashMap<>();
        List<List<String>> layers = topologicalSort(deps);
        
        for (List<String> layer : layers) {
            List<Future<ToolResult>> futures = layer.stream()
                .map(id -> executor.submit(() -> executeOne(callMap.get(id), results)))
                .toList();
            
            for (Future<ToolResult> f : futures) {
                try {
                    ToolResult r = f.get();
                    results.put(r.id(), r);
                } catch (InterruptedException | ExecutionException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        return calls.stream().map(c -> results.get(c.id())).filter(Objects::nonNull).toList();
    }
    
    private ToolResult executeOne(ToolCall call, Map<String, ToolResult> context) {
        long start = System.currentTimeMillis();
        try {
            // 变量替换
            Map<String, Object> args = resolveVariables(call.arguments(), context);
            
            // 获取元数据
            var meta = registry.get(call.name());
            if (meta == null) {
                return ToolResult.error(call.id(), call.name(), "Unknown function: " + call.name(), 
                    System.currentTimeMillis() - start);
            }
            
            // 参数转换
            Object[] params = convertParams(meta, args);
            
            // 执行
            Object result = meta.method().invoke(meta.instance(), params);
            return ToolResult.success(call.id(), call.name(), result, System.currentTimeMillis() - start);
            
        } catch (Exception e) {
            Throwable cause = e instanceof java.lang.reflect.InvocationTargetException ? e.getCause() : e;
            return ToolResult.error(call.id(), call.name(), cause.getMessage(), 
                System.currentTimeMillis() - start);
        }
    }
    
    private Set<String> extractDependencies(Map<String, Object> args) {
        Set<String> deps = new HashSet<>();
        for (Object v : args.values()) {
            if (v instanceof String s) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\$\\{([^}]+)\\}").matcher(s);
                while (m.find()) deps.add(m.group(1).split("\\.")[0]);
            }
        }
        return deps;
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveVariables(Map<String, Object> args, Map<String, ToolResult> context) {
        Map<String, Object> resolved = new HashMap<>();
        for (var e : args.entrySet()) {
            if (e.getValue() instanceof String s) {
                resolved.put(e.getKey(), replaceVars(s, context));
            } else {
                resolved.put(e.getKey(), e.getValue());
            }
        }
        return resolved;
    }
    
    private String replaceVars(String s, Map<String, ToolResult> context) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\$\\{([^}]+)\\}");
        java.util.regex.Matcher m = p.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String path = m.group(1);
            String id = path.split("\\.")[0];
            ToolResult r = context.get(id);
            if (r != null && r.isSuccess()) {
                m.appendReplacement(sb, r.result() != null ? r.result().toString() : "");
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }
    
    private Object[] convertParams(FunctionRegistry.FuncMeta meta, Map<String, Object> args) {
        Parameter[] ps = meta.method().getParameters();
        Object[] result = new Object[ps.length];
        
        for (int i = 0; i < ps.length; i++) {
            Parameter param = ps[i];
            Object value = null;
            
            // 1. 尝试用 @Param 注解的 name
            var ann = param.getAnnotation(com.toolcall.annotation.Param.class);
            if (ann != null && !ann.name().isEmpty()) {
                value = args.get(ann.name());
            }
            
            // 2. 如果没找到，尝试用参数索引作为 key（因为 LLM 可能按顺序传参）
            if (value == null) {
                // 按参数顺序尝试
                List<Object> argValues = new ArrayList<>(args.values());
                if (i < argValues.size()) {
                    value = argValues.get(i);
                }
            }
            
            // 3. 转换为目标类型
            if (value != null) {
                result[i] = mapper.convertValue(value, 
                    TypeFactory.defaultInstance().constructType(param.getParameterizedType()));
            } else {
                result[i] = getDefaultValue(param.getType());
            }
        }
        
        return result;
    }
    
    private Object getDefaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0;
        if (type == float.class) return 0.0f;
        return null;
    }
    
    private List<List<String>> topologicalSort(Map<String, Set<String>> graph) {
        Map<String, Integer> inDegree = new HashMap<>();
        for (String n : graph.keySet()) inDegree.put(n, 0);
        for (var e : graph.entrySet()) {
            for (String d : e.getValue()) inDegree.merge(e.getKey(), 1, Integer::sum);
        }
        
        List<List<String>> layers = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        
        while (visited.size() < graph.size()) {
            List<String> layer = inDegree.entrySet().stream()
                .filter(e -> e.getValue() == 0 && !visited.contains(e.getKey()))
                .map(Map.Entry::getKey).toList();
            
            if (layer.isEmpty() && visited.size() < graph.size()) throw new IllegalStateException("Circular");
            
            layers.add(layer);
            layer.forEach(n -> {
                visited.add(n);
                for (var e : graph.entrySet()) {
                    if (e.getValue().contains(n)) inDegree.merge(e.getKey(), -1, Integer::sum);
                }
            });
        }
        return layers;
    }
    
    public void shutdown() { executor.shutdown(); }
}
