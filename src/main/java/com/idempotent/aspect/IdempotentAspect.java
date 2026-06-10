package com.idempotent.aspect;

import com.alibaba.fastjson.JSON;
import com.idempotent.annotation.Idempotent;
import com.idempotent.config.IdempotentProperties;
import com.idempotent.enums.IdempotentTypeEnum;
import com.idempotent.exception.IdempotentException;
import com.idempotent.service.TokenService;
import com.idempotent.util.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

@Slf4j
@Aspect
@Component
public class IdempotentAspect {

    @Resource
    private TokenService tokenService;

    @Resource
    private RedisUtil redisUtil;

    @Resource
    private IdempotentProperties idempotentProperties;

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        IdempotentTypeEnum type = idempotent.type();
        String key = buildKey(joinPoint, idempotent);

        boolean valid = false;
        try {
            switch (type) {
                case TOKEN:
                    valid = handleTokenType(idempotent);
                    break;
                case PARAM:
                    valid = handleParamType(key, idempotent);
                    break;
                case TOKEN_AND_PARAM:
                    valid = handleTokenAndParamType(joinPoint, idempotent, key);
                    break;
                default:
                    valid = handleTokenType(idempotent);
            }

            if (!valid) {
                throw new IdempotentException(idempotent.message());
            }

            Object result = joinPoint.proceed();
            return result;
        } finally {
            if (valid && idempotent.deleteKeyWhenFinish()) {
                cleanup(idempotent, key);
            }
        }
    }

    private boolean handleTokenType(Idempotent idempotent) {
        String token = getTokenFromRequest();
        if (StringUtils.isBlank(token)) {
            throw new IdempotentException(400, "幂等令牌不能为空");
        }
        return tokenService.checkToken(token);
    }

    private boolean handleParamType(String key, Idempotent idempotent) {
        long expireTime = idempotent.expireTime();
        TimeUnit timeUnit = idempotent.timeUnit();
        Boolean success = redisUtil.setIfAbsent(key, "1", expireTime, timeUnit);
        return success != null && success;
    }

    private boolean handleTokenAndParamType(ProceedingJoinPoint joinPoint, Idempotent idempotent, String key) {
        String token = getTokenFromRequest();
        if (StringUtils.isBlank(token)) {
            throw new IdempotentException(400, "幂等令牌不能为空");
        }
        boolean tokenValid = tokenService.checkToken(token);
        if (!tokenValid) {
            return false;
        }
        long expireTime = idempotent.expireTime();
        TimeUnit timeUnit = idempotent.timeUnit();
        Boolean success = redisUtil.setIfAbsent(key, "1", expireTime, timeUnit);
        if (success == null || !success) {
            return false;
        }
        return true;
    }

    private String buildKey(ProceedingJoinPoint joinPoint, Idempotent idempotent) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String className = method.getDeclaringClass().getSimpleName();
        String methodName = method.getName();

        String prefix = StringUtils.isNotBlank(idempotent.prefix()) ? idempotent.prefix() : "idempotent";
        String key = StringUtils.isNotBlank(idempotent.key()) ? idempotent.key() : (className + ":" + methodName);

        String paramKey = "";
        if (StringUtils.isNotBlank(idempotent.paramName())) {
            Object[] args = joinPoint.getArgs();
            String[] paramNames = signature.getParameterNames();
            boolean found = false;
            for (int i = 0; i < paramNames.length; i++) {
                if (idempotent.paramName().equals(paramNames[i])) {
                    paramKey = args[i] != null ? String.valueOf(args[i].hashCode()) : "";
                    found = true;
                    break;
                }
            }
            if (!found) {
                for (Object arg : args) {
                    if (arg != null && !isRequestOrResponse(arg)) {
                        Object fieldValue = getFieldValue(arg, idempotent.paramName());
                        if (fieldValue != null) {
                            paramKey = String.valueOf(fieldValue.hashCode());
                            break;
                        }
                    }
                }
            }
        } else {
            Object[] args = joinPoint.getArgs();
            if (args != null && args.length > 0) {
                StringBuilder sb = new StringBuilder();
                for (Object arg : args) {
                    if (arg != null && !isRequestOrResponse(arg)) {
                        sb.append(JSON.toJSONString(arg).hashCode());
                    }
                }
                paramKey = sb.toString();
            }
        }

        if (StringUtils.isNotBlank(paramKey)) {
            return prefix + ":" + key + ":" + paramKey;
        }
        return prefix + ":" + key;
    }

    private Object getFieldValue(Object obj, String fieldName) {
        if (obj == null || StringUtils.isBlank(fieldName)) {
            return null;
        }
        Class<?> clazz = obj.getClass();
        while (clazz != null && clazz != Object.class) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(obj);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            } catch (IllegalAccessException e) {
                log.warn("获取字段值失败: {}", fieldName, e);
                return null;
            }
        }
        return null;
    }

    private void cleanup(Idempotent idempotent, String key) {
        try {
            IdempotentTypeEnum type = idempotent.type();
            if (type == IdempotentTypeEnum.TOKEN || type == IdempotentTypeEnum.TOKEN_AND_PARAM) {
                String token = getTokenFromRequest();
                if (StringUtils.isNotBlank(token)) {
                    tokenService.deleteToken(token);
                }
            }
            if (type == IdempotentTypeEnum.PARAM || type == IdempotentTypeEnum.TOKEN_AND_PARAM) {
                redisUtil.delete(key);
            }
        } catch (Exception e) {
            log.warn("清理幂等标识失败", e);
        }
    }

    private String getTokenFromRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        String token = request.getHeader(idempotentProperties.getHeaderName());
        if (StringUtils.isBlank(token)) {
            token = request.getParameter(idempotentProperties.getHeaderName());
        }
        return token;
    }

    private boolean isRequestOrResponse(Object obj) {
        return obj instanceof javax.servlet.http.HttpServletRequest
                || obj instanceof javax.servlet.http.HttpServletResponse;
    }
}
