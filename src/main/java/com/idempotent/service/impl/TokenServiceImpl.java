package com.idempotent.service.impl;

import com.idempotent.config.IdempotentProperties;
import com.idempotent.exception.IdempotentException;
import com.idempotent.service.TokenService;
import com.idempotent.util.RedisUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class TokenServiceImpl implements TokenService {

    @Resource
    private RedisUtil redisUtil;

    @Resource
    private IdempotentProperties idempotentProperties;

    @Override
    public String generateToken() {
        String token = UUID.randomUUID().toString().replace("-", "");
        String key = buildKey(token);
        redisUtil.set(key, token, idempotentProperties.getExpireTime(), TimeUnit.SECONDS);
        return token;
    }

    @Override
    public String generateToken(String businessKey) {
        if (StringUtils.isBlank(businessKey)) {
            return generateToken();
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        String key = buildKey(businessKey + ":" + token);
        redisUtil.set(key, token, idempotentProperties.getExpireTime(), TimeUnit.SECONDS);
        return token;
    }

    @Override
    public boolean checkToken(String token) {
        if (StringUtils.isBlank(token)) {
            throw new IdempotentException(400, "幂等令牌不能为空");
        }
        String key = buildKey(token);
        if (!redisUtil.hasKey(key)) {
            return false;
        }
        Boolean success = redisUtil.setIfAbsent(key + ":lock", "1", 10, TimeUnit.SECONDS);
        if (success == null || !success) {
            return false;
        }
        if (!redisUtil.hasKey(key)) {
            redisUtil.delete(key + ":lock");
            return false;
        }
        return true;
    }

    @Override
    public boolean checkToken(String token, String businessKey) {
        if (StringUtils.isBlank(businessKey)) {
            return checkToken(token);
        }
        if (StringUtils.isBlank(token)) {
            throw new IdempotentException(400, "幂等令牌不能为空");
        }
        String key = buildKey(businessKey + ":" + token);
        if (!redisUtil.hasKey(key)) {
            return false;
        }
        Boolean success = redisUtil.setIfAbsent(key + ":lock", "1", 10, TimeUnit.SECONDS);
        if (success == null || !success) {
            return false;
        }
        if (!redisUtil.hasKey(key)) {
            redisUtil.delete(key + ":lock");
            return false;
        }
        return true;
    }

    @Override
    public boolean deleteToken(String token) {
        String key = buildKey(token);
        boolean result = redisUtil.delete(key);
        redisUtil.delete(key + ":lock");
        return result;
    }

    @Override
    public boolean deleteToken(String token, String businessKey) {
        if (StringUtils.isBlank(businessKey)) {
            return deleteToken(token);
        }
        String key = buildKey(businessKey + ":" + token);
        boolean result = redisUtil.delete(key);
        redisUtil.delete(key + ":lock");
        return result;
    }

    private String buildKey(String token) {
        return idempotentProperties.getPrefix() + token;
    }
}
