package com.idempotent.service;

import com.idempotent.dto.IdempotentHistoryDTO;
import com.idempotent.dto.IdempotentRecord;
import com.idempotent.dto.IdempotentStatusDTO;
import com.idempotent.enums.IdempotentTypeEnum;

public interface TokenService {

    String generateToken();

    String generateToken(String businessKey);

    boolean checkToken(String token);

    boolean checkToken(String token, String businessKey);

    boolean deleteToken(String token);

    boolean deleteToken(String token, String businessKey);

    IdempotentStatusDTO getStatusByToken(String token);

    IdempotentStatusDTO getStatusByBusinessKey(String businessKey);

    IdempotentHistoryDTO getHistoryByBusinessKey(String businessKey, int page, int size);

    void markProcessing(String token, String businessKey, IdempotentTypeEnum type, IdempotentRecord record);

    void markCompleted(String token, String businessKey, IdempotentRecord record);

    void markFailed(String token, String businessKey, String errorMsg, IdempotentRecord record);
}
