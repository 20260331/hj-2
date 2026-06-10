package com.idempotent.annotation;

import com.idempotent.enums.IdempotentTypeEnum;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    IdempotentTypeEnum type() default IdempotentTypeEnum.TOKEN;

    String prefix() default "";

    String key() default "";

    String paramName() default "";

    long expireTime() default 600;

    TimeUnit timeUnit() default TimeUnit.SECONDS;

    String message() default "请勿重复提交";

    boolean deleteKeyWhenFinish() default true;
}
