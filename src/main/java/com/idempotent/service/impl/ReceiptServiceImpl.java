package com.idempotent.service.impl;

import com.alibaba.fastjson.JSON;
import com.idempotent.config.IdempotentProperties;
import com.idempotent.config.ReceiptProperties;
import com.idempotent.dto.*;
import com.idempotent.enums.IdempotentStatusEnum;
import com.idempotent.service.ReceiptService;
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
public class ReceiptServiceImpl implements ReceiptService {

    private static final String INDEX_PREFIX = "idx:";
    private static final String RECORD_PREFIX = "record:";
    private static final String HISTORY_PREFIX = "history:";

    @Resource
    private TokenService tokenService;

    @Resource
    private RedisUtil redisUtil;

    @Resource
    private IdempotentProperties idempotentProperties;

    @Resource
    private ReceiptProperties receiptProperties;

    @Override
    public ReceiptDTO registerReceipt(ReceiptRegisterDTO dto) {
        String businessKey = dto.getBusinessKey();
        String token = dto.getToken();

        if (StringUtils.isBlank(token)) {
            token = resolveTokenByBusinessKey(businessKey);
        }
        if (StringUtils.isBlank(token)) {
            token = UUID.randomUUID().toString().replace("-", "");
            createTokenIndex(businessKey, token);
        }

        IdempotentRecord record = tokenService.getRecordByToken(token);
        if (record == null) {
            record = new IdempotentRecord();
            record.setToken(token);
            record.setBusinessKey(businessKey);
            record.setCreateTime(System.currentTimeMillis());
        }

        IdempotentStatusEnum statusEnum = IdempotentStatusEnum.getByCode(dto.getStatus());
        if (statusEnum == null || statusEnum == IdempotentStatusEnum.NOT_FOUND) {
            if (dto.getErrorMsg() != null) {
                statusEnum = IdempotentStatusEnum.FAILED;
            } else if (dto.getResponseCode() != null && dto.getResponseCode() >= 400) {
                statusEnum = IdempotentStatusEnum.FAILED;
            } else {
                statusEnum = IdempotentStatusEnum.COMPLETED;
            }
        }

        record.setBusinessKey(businessKey);
        record.setStatus(statusEnum);
        record.setResponseCode(dto.getResponseCode());
        record.setResponseMessage(dto.getResponseMessage());
        record.setErrorMsg(dto.getErrorMsg());

        if (StringUtils.isNotBlank(dto.getResponseContent())) {
            record.setResponseContent(dto.getResponseContent());
        }
        record.setReceiptSource(StringUtils.isNotBlank(dto.getReceiptSource())
                ? dto.getReceiptSource() : receiptProperties.getDefaultSource());
        record.setReceiptTime(System.currentTimeMillis());
        if (record.getProcessEndTime() == null) {
            record.setProcessEndTime(System.currentTimeMillis());
        }

        saveReceiptRecord(token, record);
        addReceiptToHistory(businessKey, token,
                record.getCreateTime() != null ? record.getCreateTime() : System.currentTimeMillis());

        Long expireSeconds = redisUtil.getExpire(buildRecordKey(token));
        return ReceiptDTO.fromRecord(record, expireSeconds);
    }

    @Override
    public ReceiptDTO getReceiptByToken(String token) {
        if (StringUtils.isBlank(token)) {
            return ReceiptDTO.fromRecord(null, 0L);
        }
        IdempotentRecord record = tokenService.getRecordByToken(token);
        if (record == null) {
            return ReceiptDTO.fromRecord(null, 0L);
        }
        Long expireSeconds = redisUtil.getExpire(buildRecordKey(token));
        return ReceiptDTO.fromRecord(record, expireSeconds);
    }

    @Override
    public ReceiptDTO getReceiptByBusinessKey(String businessKey) {
        if (StringUtils.isBlank(businessKey)) {
            return ReceiptDTO.fromRecord(null, 0L);
        }
        IdempotentRecord record = tokenService.getLatestRecordByBusinessKey(businessKey);
        if (record == null) {
            return ReceiptDTO.fromRecord(null, 0L);
        }
        Long expireSeconds = 0L;
        if (record.getToken() != null) {
            expireSeconds = redisUtil.getExpire(buildRecordKey(record.getToken()));
        }
        return ReceiptDTO.fromRecord(record, expireSeconds);
    }

    @Override
    public ReceiptListDTO getReceiptHistory(String businessKey, int page, int size) {
        if (StringUtils.isBlank(businessKey)) {
            return ReceiptListDTO.of(null, 0L, Collections.emptyList());
        }
        String historyKey = buildHistoryKey(businessKey);
        Long total = redisUtil.zSize(historyKey);
        if (total == null) total = 0L;

        int start = (Math.max(page, 1) - 1) * Math.max(size, 1);
        int end = start + Math.max(size, 1) - 1;

        Set<Object> tokens = redisUtil.zReverseRange(historyKey, start, end);
        List<ReceiptDTO> records = new ArrayList<>();
        if (tokens != null) {
            for (Object tokenObj : tokens) {
                IdempotentRecord record = tokenService.getRecordByToken(String.valueOf(tokenObj));
                if (record != null) {
                    Long expireSeconds = redisUtil.getExpire(buildRecordKey(record.getToken()));
                    records.add(ReceiptDTO.fromRecord(record, expireSeconds));
                }
            }
        }
        return ReceiptListDTO.of(businessKey, total, records);
    }

    private String resolveTokenByBusinessKey(String businessKey) {
        if (StringUtils.isBlank(businessKey)) {
            return null;
        }
        String indexKey = buildIndexKey(businessKey);
        Object tokenObj = redisUtil.get(indexKey);
        if (tokenObj != null) {
            return String.valueOf(tokenObj);
        }
        return null;
    }

    private void createTokenIndex(String businessKey, String token) {
        if (StringUtils.isBlank(businessKey) || StringUtils.isBlank(token)) {
            return;
        }
        String indexKey = buildIndexKey(businessKey);
        redisUtil.set(indexKey, token, receiptProperties.getExpireDays(), TimeUnit.DAYS);
    }

    private void saveReceiptRecord(String token, IdempotentRecord record) {
        try {
            String recordKey = buildRecordKey(token);
            Map<String, Object> map = new HashMap<>();
            map.put("token", record.getToken());
            map.put("businessKey", record.getBusinessKey() != null ? record.getBusinessKey() : "");
            map.put("type", record.getType() != null ? record.getType().name() : "");
            map.put("status", record.getStatus() != null ? record.getStatus().getCode() : IdempotentStatusEnum.COMPLETED.getCode());
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
            redisUtil.expire(recordKey, receiptProperties.getExpireDays(), TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("保存回执记录失败, token={}", token, e);
        }
    }

    private void addReceiptToHistory(String businessKey, String token, long timestamp) {
        if (StringUtils.isBlank(businessKey) || StringUtils.isBlank(token)) {
            return;
        }
        try {
            String historyKey = buildHistoryKey(businessKey);
            redisUtil.zAdd(historyKey, token, timestamp);
            redisUtil.expire(historyKey, receiptProperties.getExpireDays(), TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("回执添加历史记录失败, businessKey={}, token={}", businessKey, token, e);
        }
    }

    private String buildIndexKey(String businessKey) {
        return receiptProperties.getPrefix() + INDEX_PREFIX + businessKey;
    }

    private String buildRecordKey(String token) {
        return receiptProperties.getPrefix() + RECORD_PREFIX + token;
    }

    private String buildHistoryKey(String businessKey) {
        return receiptProperties.getPrefix() + HISTORY_PREFIX + businessKey;
    }
}
