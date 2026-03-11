package com.toolcall.annotation;

import java.lang.annotation.*;

/**
 * 标记方法参数为工具参数
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Param {
    String name() default "";
    String description() default "";
    String type() default "string";
    boolean required() default true;
    String defaultValue() default "";
}
