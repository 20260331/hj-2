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

    private static final String INDEX_PREFIX = "idx:";

    @Resource
    private RedisUtil redisUtil;

    @Resource
    private IdempotentProperties idempotentProperties;

    @Override
    public String generateToken() {
        String token = UUID.randomUUID().toString().replace("-", "");
        String key = buildKey(token);
        redisUtil.set(key, "1", idempotentProperties.getExpireTime(), TimeUnit.SECONDS);
        return token;
    }

    @Override
    public String generateToken(String businessKey) {
        if (StringUtils.isBlank(businessKey)) {
            return generateToken();
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        String key = buildKey(token);
        String indexKey = buildIndexKey(businessKey);
        redisUtil.set(key, businessKey, idempotentProperties.getExpireTime(), TimeUnit.SECONDS);
        redisUtil.set(indexKey, token, idempotentProperties.getExpireTime(), TimeUnit.SECONDS);
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
        String key = buildKey(token);
        Object value = redisUtil.get(key);
        if (value == null) {
            return false;
        }
        if (!businessKey.equals(String.valueOf(value))) {
            return false;
        }
        Boolean success = redisUtil.setIfAbsent(key + ":lock", "1", 10, TimeUnit.SECONDS);
        if (success == null || !success) {
            return false;
        }
        value = redisUtil.get(key);
        if (value == null || !businessKey.equals(String.valueOf(value))) {
            redisUtil.delete(key + ":lock");
            return false;
        }
        return true;
    }

    @Override
    public boolean deleteToken(String token) {
        String key = buildKey(token);
        Object value = redisUtil.get(key);
        boolean result = redisUtil.delete(key);
        redisUtil.delete(key + ":lock");
        if (value != null && !"1".equals(String.valueOf(value))) {
            String indexKey = buildIndexKey(String.valueOf(value));
            redisUtil.delete(indexKey);
        }
        return result;
    }

    @Override
    public boolean deleteToken(String token, String businessKey) {
        if (StringUtils.isBlank(businessKey)) {
            return deleteToken(token);
        }
        String key = buildKey(token);
        String indexKey = buildIndexKey(businessKey);
        boolean result = redisUtil.delete(key);
        redisUtil.delete(key + ":lock");
        redisUtil.delete(indexKey);
        return result;
    }

    private String buildKey(String token) {
        return idempotentProperties.getPrefix() + token;
    }

    private String buildIndexKey(String businessKey) {
        return idempotentProperties.getPrefix() + INDEX_PREFIX + businessKey;
    }
}
