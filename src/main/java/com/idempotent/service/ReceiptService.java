package com.idempotent.service;

import com.idempotent.dto.ReceiptDTO;
import com.idempotent.dto.ReceiptListDTO;
import com.idempotent.dto.ReceiptRegisterDTO;

public interface ReceiptService {

    ReceiptDTO registerReceipt(ReceiptRegisterDTO dto);

    ReceiptDTO getReceiptByToken(String token);

    ReceiptDTO getReceiptByBusinessKey(String businessKey);

    ReceiptListDTO getReceiptHistory(String businessKey, int page, int size);
}
