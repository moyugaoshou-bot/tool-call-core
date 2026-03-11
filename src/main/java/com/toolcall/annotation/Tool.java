package com.toolcall.annotation;

import java.lang.annotation.*;

/**
 * 标记方法为可调用的工具
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Tool {
    /** 工具名称，默认使用方法名 */
    String name() default "";
    /** 工具描述，用于提示词 */
    String description() default "";
}
