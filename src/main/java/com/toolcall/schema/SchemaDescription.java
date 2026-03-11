package com.toolcall.schema;

import java.lang.annotation.*;

/**
 * 字段描述注解
 * 用于为 Java Bean 字段添加描述
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SchemaDescription {
    String value();
}
