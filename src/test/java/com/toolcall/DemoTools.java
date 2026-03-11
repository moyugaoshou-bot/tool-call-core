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
        @Param(name = "unit", description = "温度单位", type = "string", required = false, defaultValue = "celsius") String unit
    ) {
        return "天气晴，25°C";
    }
    
    @Tool(name = "search_hotels", description = "搜索酒店")
    public List<Map<String, Object>> searchHotels(
        @Param(name = "city", description = "城市", type = "string", required = true) String city,
        @Param(name = "min_price", description = "最低价", type = "integer", required = false, defaultValue = "0") Integer minPrice
    ) {
        return List.of(Map.of("name", "Hotel A", "price", 500), Map.of("name", "Hotel B", "price", 300));
    }
    
    @Tool(name = "book_hotel", description = "预订酒店")
    public String bookHotel(
        @Param(name = "hotel_name", description = "酒店名", type = "string", required = true) String hotelName,
        @Param(name = "nights", description = "天数", type = "integer", required = true) Integer nights
    ) {
        return "已预订 " + hotelName + "，" + nights + "晚，确认码: ABC123";
    }
}
