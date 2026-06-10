package com.idempotent.service.impl;

import com.alibaba.fastjson.JSON;
import com.idempotent.config.IdempotentProperties;
import com.idempotent.dto.IdempotentHistoryDTO;
import com.idempotent.dto.IdempotentRecord;
import com.idempotent.dto.IdempotentStatusDTO;
import com.idempotent.enums.IdempotentStatusEnum;
import com.idempotent.enums.IdempotentTypeEnum;
import com.idempotent.exception.IdempotentException;
import com.idempotent.service.TokenService;
import com.idempotent.util.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class TokenServiceImpl implements TokenService {

    private static final String INDEX_PREFIX = "idx:";
    private static final String RECORD_PREFIX = "record:";
    private static final String HISTORY_PREFIX = "history:";
    private static final long HISTORY_EXPIRE_DAYS = 30;

    @Resource
    private RedisUtil redisUtil;

    @Resource
    private IdempotentProperties idempotentProperties;

    @Override
    public String generateToken() {
        String token = UUID.randomUUID().toString().replace("-", "");
        String key = buildKey(token);
        redisUtil.set(key, "1", idempotentProperties.getExpireTime(), TimeUnit.SECONDS);
        initRecord(token, null, null);
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
        initRecord(token, businessKey, null);
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

    @Override
    public IdempotentStatusDTO getStatusByToken(String token) {
        if (StringUtils.isBlank(token)) {
            return IdempotentStatusDTO.of(token, null, IdempotentStatusEnum.NOT_FOUND, 0L, null);
        }
        String key = buildKey(token);
        boolean tokenExists = redisUtil.hasKey(key);
        Long expireSeconds = redisUtil.getExpire(key);
        if (expireSeconds == null) expireSeconds = 0L;

        IdempotentRecord record = getRecord(token);

        if (record == null) {
            if (tokenExists) {
                return IdempotentStatusDTO.of(token, null, IdempotentStatusEnum.PROCESSING, expireSeconds, null);
            }
            return IdempotentStatusDTO.of(token, null, IdempotentStatusEnum.NOT_FOUND, 0L, null);
        }

        String businessKey = record.getBusinessKey();
        IdempotentStatusEnum status = record.getStatus();
        if (!tokenExists && status != IdempotentStatusEnum.COMPLETED) {
            status = IdempotentStatusEnum.TOKEN_EXPIRED;
        }
        return IdempotentStatusDTO.of(token, businessKey, status, expireSeconds, record);
    }

    @Override
    public IdempotentStatusDTO getStatusByBusinessKey(String businessKey) {
        if (StringUtils.isBlank(businessKey)) {
            return IdempotentStatusDTO.of(null, businessKey, IdempotentStatusEnum.NOT_FOUND, 0L, null);
        }
        String indexKey = buildIndexKey(businessKey);
        Object tokenObj = redisUtil.get(indexKey);
        if (tokenObj == null) {
            IdempotentRecord lastRecord = getLastHistoryRecord(businessKey);
            if (lastRecord != null) {
                IdempotentStatusEnum status = lastRecord.getStatus() != null ? lastRecord.getStatus() : IdempotentStatusEnum.COMPLETED;
                return IdempotentStatusDTO.of(lastRecord.getToken(), businessKey, status, 0L, lastRecord);
            }
            return IdempotentStatusDTO.of(null, businessKey, IdempotentStatusEnum.NOT_FOUND, 0L, null);
        }
        return getStatusByToken(String.valueOf(tokenObj));
    }

    @Override
    public IdempotentHistoryDTO getHistoryByBusinessKey(String businessKey, int page, int size) {
        if (StringUtils.isBlank(businessKey)) {
            return IdempotentHistoryDTO.of(null, 0L, Collections.emptyList());
        }
        String historyKey = buildHistoryKey(businessKey);
        Long total = redisUtil.zSize(historyKey);
        if (total == null) total = 0L;

        int start = (Math.max(page, 1) - 1) * Math.max(size, 1);
        int end = start + Math.max(size, 1) - 1;

        Set<Object> tokens = redisUtil.zReverseRange(historyKey, start, end);
        List<IdempotentRecord> records = new ArrayList<>();
        if (tokens != null) {
            for (Object tokenObj : tokens) {
                IdempotentRecord record = getRecord(String.valueOf(tokenObj));
                if (record != null) {
                    records.add(record);
                }
            }
        }
        return IdempotentHistoryDTO.of(businessKey, total, records);
    }

    @Override
    public void markProcessing(String token, String businessKey, IdempotentTypeEnum type, IdempotentRecord record) {
        if (StringUtils.isBlank(token)) {
            return;
        }
        IdempotentRecord existing = getRecord(token);
        if (record == null) {
            record = existing != null ? existing : new IdempotentRecord();
        }
        record.setToken(token);
        String effectiveBusinessKey = resolveEffectiveBusinessKey(businessKey, record, existing);
        record.setBusinessKey(effectiveBusinessKey);
        if (type != null) {
            record.setType(type);
        }
        record.setStatus(IdempotentStatusEnum.PROCESSING);
        if (record.getCreateTime() == null) {
            record.setCreateTime(existing != null && existing.getCreateTime() != null ? existing.getCreateTime() : System.currentTimeMillis());
        }
        record.setProcessStartTime(System.currentTimeMillis());
        saveRecord(token, record);
    }

    @Override
    public void markCompleted(String token, String businessKey, IdempotentRecord record) {
        markCompleted(token, businessKey, record, null);
    }

    @Override
    public void markCompleted(String token, String businessKey, IdempotentRecord record, Object response) {
        if (StringUtils.isBlank(token)) {
            return;
        }
        IdempotentRecord existing = getRecord(token);
        if (record == null) {
            record = existing != null ? existing : new IdempotentRecord();
        }
        record.setToken(token);
        String effectiveBusinessKey = resolveEffectiveBusinessKey(businessKey, record, existing);
        record.setBusinessKey(effectiveBusinessKey);
        record.setStatus(IdempotentStatusEnum.COMPLETED);
        record.setProcessEndTime(System.currentTimeMillis());
        record.setReceiptTime(System.currentTimeMillis());
        record.setReceiptSource("AUTO_ASPECT");
        if (response != null) {
            try {
                if (response instanceof com.idempotent.util.Result) {
                    com.idempotent.util.Result<?> result = (com.idempotent.util.Result<?>) response;
                    record.setResponseCode(result.getCode());
                    record.setResponseMessage(result.getMessage());
                    if (result.getData() != null) {
                        record.setResponseContent(com.alibaba.fastjson.JSON.toJSONString(result.getData()));
                    } else {
                        record.setResponseContent(com.alibaba.fastjson.JSON.toJSONString(result));
                    }
                } else {
                    record.setResponseContent(com.alibaba.fastjson.JSON.toJSONString(response));
                    record.setResponseCode(200);
                }
            } catch (Exception e) {
                log.warn("序列化返回结果失败", e);
                record.setResponseContent(String.valueOf(response));
            }
        }
        saveRecord(token, record);
        addToHistory(effectiveBusinessKey, token, record.getCreateTime() != null ? record.getCreateTime() : System.currentTimeMillis());
    }

    @Override
    public void markFailed(String token, String businessKey, String errorMsg, IdempotentRecord record) {
        if (StringUtils.isBlank(token)) {
            return;
        }
        IdempotentRecord existing = getRecord(token);
        if (record == null) {
            record = existing != null ? existing : new IdempotentRecord();
        }
        record.setToken(token);
        String effectiveBusinessKey = resolveEffectiveBusinessKey(businessKey, record, existing);
        record.setBusinessKey(effectiveBusinessKey);
        record.setStatus(IdempotentStatusEnum.FAILED);
        record.setProcessEndTime(System.currentTimeMillis());
        record.setErrorMsg(errorMsg);
        record.setReceiptTime(System.currentTimeMillis());
        record.setReceiptSource(idempotentProperties.getPrefix().contains("token") ? "AUTO_ASPECT" : "AUTO_ASPECT");
        saveRecord(token, record);
        addToHistory(effectiveBusinessKey, token, record.getCreateTime() != null ? record.getCreateTime() : System.currentTimeMillis());
    }

    private void initRecord(String token, String businessKey, IdempotentTypeEnum type) {
        IdempotentRecord record = new IdempotentRecord();
        record.setToken(token);
        record.setBusinessKey(businessKey);
        record.setType(type);
        record.setStatus(IdempotentStatusEnum.PROCESSING);
        record.setCreateTime(System.currentTimeMillis());
        record.setExpireTime(idempotentProperties.getExpireTime());
        saveRecord(token, record);
    }

    private void saveRecord(String token, IdempotentRecord record) {
        try {
            String recordKey = buildRecordKey(token);
            Map<String, Object> map = new HashMap<>();
            map.put("token", record.getToken());
            map.put("businessKey", record.getBusinessKey() != null ? record.getBusinessKey() : "");
            map.put("type", record.getType() != null ? record.getType().name() : "");
            map.put("status", record.getStatus() != null ? record.getStatus().getCode() : IdempotentStatusEnum.NOT_FOUND.getCode());
            map.put("className", record.getClassName() != null ? record.getClassName() : "");
            map.put("methodName", record.getMethodName() != null ? record.getMethodName() : "");
            map.put("requestParam", record.getRequestParam() != null ? record.getRequestParam() : "");
            map.put("createTime", record.getCreateTime() != null ? record.getCreateTime() : 0L);
            map.put("processStartTime", record.getProcessStartTime() != null ? record.getProcessStartTime() : 0L);
            map.put("processEndTime", record.getProcessEndTime() != null ? record.getProcessEndTime() : 0L);
            map.put("expireTime", record.getExpireTime() != null ? record.getExpireTime() : 0L);
            map.put("errorMsg", record.getErrorMsg() != null ? record.getErrorMsg() : "");
            map.put("responseContent", record.getResponseContent() != null ? record.getResponseContent() : "");
            map.put("receiptSource", record.getReceiptSource() != null ? record.getReceiptSource() : "");
            map.put("receiptTime", record.getReceiptTime() != null ? record.getReceiptTime() : 0L);
            map.put("responseCode", record.getResponseCode() != null ? record.getResponseCode() : 0);
            map.put("responseMessage", record.getResponseMessage() != null ? record.getResponseMessage() : "");
            redisUtil.hPutAll(recordKey, map);
            redisUtil.expire(recordKey, HISTORY_EXPIRE_DAYS, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("保存幂等记录失败, token={}", token, e);
        }
    }

    private IdempotentRecord getRecord(String token) {
        try {
            String recordKey = buildRecordKey(token);
            if (!redisUtil.hHasKey(recordKey, "token")) {
                return null;
            }
            Map<Object, Object> map = redisUtil.hGetAll(recordKey);
            if (map == null || map.isEmpty()) {
                return null;
            }
            IdempotentRecord record = new IdempotentRecord();
            record.setToken(getMapStr(map, "token"));
            record.setBusinessKey(getMapStr(map, "businessKey"));
            String typeStr = getMapStr(map, "type");
            if (StringUtils.isNotBlank(typeStr)) {
                record.setType(IdempotentTypeEnum.valueOf(typeStr));
            }
            Integer statusCode = getMapInt(map, "status");
            if (statusCode != null) {
                record.setStatus(IdempotentStatusEnum.getByCode(statusCode));
            }
            record.setClassName(getMapStr(map, "className"));
            record.setMethodName(getMapStr(map, "methodName"));
            record.setRequestParam(getMapStr(map, "requestParam"));
            record.setCreateTime(getMapLong(map, "createTime"));
            record.setProcessStartTime(getMapLong(map, "processStartTime"));
            record.setProcessEndTime(getMapLong(map, "processEndTime"));
            record.setExpireTime(getMapLong(map, "expireTime"));
            record.setErrorMsg(getMapStr(map, "errorMsg"));
            record.setResponseContent(getMapStr(map, "responseContent"));
            record.setReceiptSource(getMapStr(map, "receiptSource"));
            record.setReceiptTime(getMapLong(map, "receiptTime"));
            record.setResponseCode(getMapInt(map, "responseCode"));
            record.setResponseMessage(getMapStr(map, "responseMessage"));
            return record;
        } catch (Exception e) {
            log.warn("获取幂等记录失败, token={}", token, e);
            return null;
        }
    }

    @Override
    public IdempotentRecord getLatestRecordByBusinessKey(String businessKey) {
        if (StringUtils.isBlank(businessKey)) {
            return null;
        }
        String indexKey = buildIndexKey(businessKey);
        Object tokenObj = redisUtil.get(indexKey);
        if (tokenObj != null) {
            IdempotentRecord record = getRecord(String.valueOf(tokenObj));
            if (record != null) {
                return record;
            }
        }
        return getLastHistoryRecord(businessKey);
    }

    @Override
    public IdempotentRecord getRecordByToken(String token) {
        if (StringUtils.isBlank(token)) {
            return null;
        }
        return getRecord(token);
    }

    private IdempotentRecord getLastHistoryRecord(String businessKey) {
        try {
            String historyKey = buildHistoryKey(businessKey);
            Set<Object> tokens = redisUtil.zReverseRange(historyKey, 0, 0);
            if (tokens != null && !tokens.isEmpty()) {
                Object tokenObj = tokens.iterator().next();
                return getRecord(String.valueOf(tokenObj));
            }
        } catch (Exception e) {
            log.warn("获取历史记录失败, businessKey={}", businessKey, e);
        }
        return null;
    }

    private void addToHistory(String businessKey, String token, long timestamp) {
        if (StringUtils.isBlank(businessKey) || StringUtils.isBlank(token)) {
            return;
        }
        try {
            String historyKey = buildHistoryKey(businessKey);
            redisUtil.zAdd(historyKey, token, timestamp);
            redisUtil.expire(historyKey, HISTORY_EXPIRE_DAYS, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("添加历史记录失败, businessKey={}, token={}", businessKey, token, e);
        }
    }

    private String resolveEffectiveBusinessKey(String businessKey, IdempotentRecord record, IdempotentRecord existing) {
        if (StringUtils.isNotBlank(businessKey)) {
            return businessKey;
        }
        if (record != null && StringUtils.isNotBlank(record.getBusinessKey())) {
            return record.getBusinessKey();
        }
        if (existing != null && StringUtils.isNotBlank(existing.getBusinessKey())) {
            return existing.getBusinessKey();
        }
        return null;
    }

    private String getMapStr(Map<Object, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : null;
    }

    private Long getMapLong(Map<Object, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return null;
        try {
            return Long.valueOf(String.valueOf(val));
        } catch (Exception e) {
            return null;
        }
    }

    private Integer getMapInt(Map<Object, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return null;
        try {
            return Integer.valueOf(String.valueOf(val));
        } catch (Exception e) {
            return null;
        }
    }

    private String buildKey(String token) {
        return idempotentProperties.getPrefix() + token;
    }

    private String buildIndexKey(String businessKey) {
        return idempotentProperties.getPrefix() + INDEX_PREFIX + businessKey;
    }

    private String buildRecordKey(String token) {
        return idempotentProperties.getPrefix() + RECORD_PREFIX + token;
    }

    private String buildHistoryKey(String businessKey) {
        return idempotentProperties.getPrefix() + HISTORY_PREFIX + businessKey;
    }
}
