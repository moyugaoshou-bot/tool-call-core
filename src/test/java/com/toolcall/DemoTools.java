package com.toolcall;

import com.toolcall.annotation.Param;
import com.toolcall.annotation.Tool;
import com.toolcall.model.ToolCall;
import com.toolcall.model.ToolResult;

import java.util.List;
import java.util.Map;

/**
 * 测试用工具类
 */
class DemoTools {
    
    @Tool(name = "get_weather", description = "获取指定城市天气信息")
    public String getWeather(
        @Param(name = "city", description = "城市名称", type = "string", required = true) String city,
        @Param(name = "unit", description = "温度单位: celsius/fahrenheit", type = "string", required = false, defaultValue = "celsius") String unit
    ) {
        return "天气晴，25°C";
    }
    
    @Tool(name = "search_hotels", description = "搜索酒店")
    public List<Map<String, Object>> searchHotels(
        @Param(name = "city", description = "城市", type = "string", required = true) String city,
        @Param(name = "min_price", description = "最低价", type = "integer", required = false, defaultValue = "0") Integer minPrice,
        @Param(name = "max_price", description = "最高价", type = "integer", required = false, defaultValue = "1000") Integer maxPrice
    ) {
        return List.of(
            Map.of("name", "Hotel A", "price", 500),
            Map.of("name", "Hotel B", "price", 300)
        );
    }
    
    @Tool(name = "book_hotel", description = "预订酒店")
    public String bookHotel(
        @Param(name = "hotel_name", description = "酒店名", type = "string", required = true) String hotelName,
        @Param(name = "nights", description = "天数", type = "integer", required = true) Integer nights
    ) {
        return "已预订 " + hotelName + "，" + nights + "晚，确认码: ABC123";
    }
    
    @Tool(name = "send_email", description = "发送邮件")
    public String sendEmail(
        @Param(name = "to", description = "收件人", type = "string", required = true) String to,
        @Param(name = "subject", description = "主题", type = "string", required = true) String subject,
        @Param(name = "body", description = "正文", type = "string", required = false, defaultValue = "") String body
    ) {
        return "邮件已发送至 " + to + "，主题: " + subject;
    }
    
    @Tool(name = "add_numbers", description = "加法计算")
    public int addNumbers(
        @Param(name = "a", description = "第一个数", type = "integer", required = true) int a,
        @Param(name = "b", description = "第二个数", type = "integer", required = true) int b
    ) {
        return a + b;
    }
    
    @Tool(name = "get_config", description = "获取配置")
    public Map<String, String> getConfig(
        @Param(name = "key", description = "配置键", type = "string", required = true) String key
    ) {
        return Map.of("key", key, "value", "config_value_" + key);
    }
}
