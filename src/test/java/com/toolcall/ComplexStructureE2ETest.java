package com.toolcall;

import com.toolcall.annotation.Param;
import com.toolcall.annotation.Tool;
import com.toolcall.model.ToolCall;
import com.toolcall.model.ToolResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 复杂嵌套结构体全链路E2E测试
 * 覆盖：注册 → 提示词生成 → LLM解析 → 工具执行
 */
class ComplexStructureE2ETest {
    
    private static ToolCallFramework framework;
    
    // ========== 复杂嵌套POJO定义 ==========
    // 使用 public 字段以便 Jackson 访问
    
    public static class Address {
        public String streetName;
        public String cityName;
        public String zipCode;
    }
    
    public static class Category {
        public String categoryName;
        public int priorityLevel;
    }
    
    public static class Product {
        public String productName;
        public double unitPrice;
        public Category productCategory;
        public List<String> productTags;
    }
    
    public static class Customer {
        public String customerName;
        public int customerAge;
        public Address homeAddress;
        public List<Product> favoriteProducts;
        public Set<String> preferredChannels;
        public Map<String, Address> deliveryAddresses;
    }
    
    // ========== 工具类 ==========
    
    static class OrderTools {
        
        @Tool(name = "create_order", description = "创建订单")
        public String createOrder(
            @Param(name = "customer", description = "客户信息", required = true) Customer customer,
            @Param(name = "product_list", description = "商品列表", required = true) List<Product> productList,
            @Param(name = "coupon_codes", description = "优惠券代码", required = false) Set<String> couponCodes
        ) {
            return String.format("订单创建成功：客户=%s, 商品数=%d", 
                customer.customerName, productList.size());
        }
        
        @Tool(name = "update_address", description = "更新地址")
        public String updateAddress(
            @Param(name = "user_id", description = "用户ID", required = true) String userId,
            @Param(name = "new_address", description = "新地址", required = true) Address newAddress
        ) {
            return String.format("地址更新：用户=%s, 街道=%s, 城市=%s",
                userId, newAddress.streetName, newAddress.cityName);
        }
    }
    
    @BeforeAll
    static void setup() {
        framework = new ToolCallFramework();
        framework.register(new OrderTools());
    }
    
    // ========== 测试用例 ==========
    
    @Test
    void test1_工具注册_复杂结构体() {
        // 验证工具已注册
        assertTrue(framework.registry().has("create_order"));
        assertTrue(framework.registry().has("update_address"));
        
        // 验证提示词包含嵌套结构
        String prompt = framework.systemPrompt();
        
        // 打印提示词以便调试
        System.out.println("=== SYSTEM PROMPT ===");
        System.out.println(prompt);
        System.out.println("=====================");
        
        // 顶层字段 - @Param注解名称
        assertTrue(prompt.contains("customer"), "缺少 customer");
        assertTrue(prompt.contains("product_list"), "缺少 product_list");
        assertTrue(prompt.contains("coupon_codes"), "缺少 coupon_codes");
        
        // 第二层嵌套 - POJO字段名
        assertTrue(prompt.contains("customerName"), "缺少 customerName");
        assertTrue(prompt.contains("homeAddress"), "缺少 homeAddress");
        assertTrue(prompt.contains("favoriteProducts"), "缺少 favoriteProducts");
        
        // 第三层嵌套（在 homeAddress 下）
        assertTrue(prompt.contains("streetName"), "缺少 streetName");
        assertTrue(prompt.contains("cityName"), "缺少 cityName");
        assertTrue(prompt.contains("zipCode"), "缺少 zipCode");
        
        // array 类型不展开内部结构，所以不检查 productCategory
        // 但会显示 favoriteProducts: array
        assertTrue(prompt.contains("favoriteProducts"), "缺少 favoriteProducts");
    }
    
    @Test
    void test2_LLM返回_snake_case_字段名转换() {
        // 模拟LLM返回snake_case格式
        String llmResponse = """
            {
                "tool_calls": [
                    {
                        "id": "call_001",
                        "name": "update_address",
                        "arguments": {
                            "user_id": "user_123",
                            "new_address": {
                                "street_name": "NanShan Road",
                                "city_name": "Shenzhen",
                                "zip_code": "518000"
                            }
                        }
                    }
                ]
            }
            """;
        
        var parsed = framework.parse(llmResponse);
        assertTrue(parsed.hasToolCalls());
        
        ToolCall call = parsed.toolCalls().get(0);
        List<ToolResult> results = framework.execute(List.of(call));
        
        assertEquals(1, results.size());
        ToolResult result = results.get(0);
        assertTrue(result.isSuccess(), "执行失败: " + result.error());
        
        // 验证字段名转换成功
        String output = (String) result.result();
        assertTrue(output.contains("NanShan Road"));
        assertTrue(output.contains("Shenzhen"));
    }
    
    @Test
    void test3_多层嵌套_工具调用() {
        // 模拟LLM返回多层嵌套数据（snake_case）
        String llmResponse = """
            {
                "tool_calls": [
                    {
                        "id": "call_002",
                        "name": "create_order",
                        "arguments": {
                            "customer": {
                                "customer_name": "张三",
                                "customer_age": 30,
                                "home_address": {
                                    "street_name": "中山路",
                                    "city_name": "广州",
                                    "zip_code": "510000"
                                },
                                "favorite_products": [
                                    {
                                        "product_name": "iPhone",
                                        "unit_price": 6999.0,
                                        "product_category": {
                                            "category_name": "电子产品",
                                            "priority_level": 1
                                        },
                                        "product_tags": ["手机", "苹果"]
                                    }
                                ],
                                "preferred_channels": ["email", "sms"],
                                "delivery_addresses": {
                                    "home": {
                                        "street_name": "家地址",
                                        "city_name": "深圳",
                                        "zip_code": "518000"
                                    }
                                }
                            },
                            "product_list": [
                                {
                                    "product_name": "MacBook",
                                    "unit_price": 12999.0,
                                    "product_category": {
                                        "category_name": "电脑",
                                        "priority_level": 2
                                    },
                                    "product_tags": ["笔记本", "苹果"]
                                }
                            ],
                            "coupon_codes": ["SAVE10", "NEWUSER"]
                        }
                    }
                ]
            }
            """;
        
        var parsed = framework.parse(llmResponse);
        assertTrue(parsed.hasToolCalls());
        
        ToolCall call = parsed.toolCalls().get(0);
        List<ToolResult> results = framework.execute(List.of(call));
        
        assertEquals(1, results.size());
        ToolResult result = results.get(0);
        assertTrue(result.isSuccess(), "执行失败: " + result.error());
        
        String output = (String) result.result();
        assertTrue(output.contains("张三"));
        assertTrue(output.contains("1")); // product_list size
    }
    
    @Test
    void test4_混合大小写_字段名() {
        // 测试混合使用camelCase和snake_case
        String llmResponse = """
            {
                "tool_calls": [
                    {
                        "id": "call_003",
                        "name": "update_address",
                        "arguments": {
                            "user_id": "user_456",
                            "new_address": {
                                "streetName": "MixedRoad",
                                "city_name": "Beijing",
                                "zipCode": "100000"
                            }
                        }
                    }
                ]
            }
            """;
        
        var parsed = framework.parse(llmResponse);
        ToolCall call = parsed.toolCalls().get(0);
        List<ToolResult> results = framework.execute(List.of(call));
        
        assertTrue(results.get(0).isSuccess());
        String output = (String) results.get(0).result();
        assertTrue(output.contains("MixedRoad"));
        assertTrue(output.contains("Beijing"));
    }
    
    @Test
    void test5_空集合_处理() {
        // 测试空List和空Map
        String llmResponse = """
            {
                "tool_calls": [
                    {
                        "id": "call_004",
                        "name": "create_order",
                        "arguments": {
                            "customer": {
                                "customer_name": "李四",
                                "customer_age": 25,
                                "home_address": {
                                    "street_name": "Test St",
                                    "city_name": "Shanghai",
                                    "zip_code": "200000"
                                },
                                "favorite_products": [],
                                "preferred_channels": [],
                                "delivery_addresses": {}
                            },
                            "product_list": [],
                            "coupon_codes": []
                        }
                    }
                ]
            }
            """;
        
        var parsed = framework.parse(llmResponse);
        ToolCall call = parsed.toolCalls().get(0);
        List<ToolResult> results = framework.execute(List.of(call));
        
        assertTrue(results.get(0).isSuccess());
    }
}
