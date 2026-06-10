package com.idempotent.controller;

import com.idempotent.dto.ReceiptDTO;
import com.idempotent.dto.ReceiptListDTO;
import com.idempotent.dto.ReceiptRegisterDTO;
import com.idempotent.service.ReceiptService;
import com.idempotent.util.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@Slf4j
@RestController
@RequestMapping("/api/receipt")
public class ReceiptController {

    @Resource
    private ReceiptService receiptService;

    @PostMapping("/register")
    public Result<ReceiptDTO> registerReceipt(@Validated @RequestBody ReceiptRegisterDTO dto) {
        log.info("回执中心-登记处理结果: businessKey={}, token={}, status={}",
                dto.getBusinessKey(), dto.getToken(), dto.getStatus());
        ReceiptDTO receipt = receiptService.registerReceipt(dto);
        return Result.success(receipt);
    }

    @GetMapping("/token/{token}")
    public Result<ReceiptDTO> getReceiptByToken(@PathVariable String token) {
        ReceiptDTO receipt = receiptService.getReceiptByToken(token);
        return Result.success(receipt);
    }

    @GetMapping("/business/{businessKey}")
    public Result<ReceiptDTO> getReceiptByBusinessKey(@PathVariable String businessKey) {
        ReceiptDTO receipt = receiptService.getReceiptByBusinessKey(businessKey);
        return Result.success(receipt);
    }

    @GetMapping("/business/{businessKey}/latest")
    public Result<ReceiptDTO> getLatestReceiptByBusinessKey(@PathVariable String businessKey) {
        ReceiptDTO receipt = receiptService.getReceiptByBusinessKey(businessKey);
        return Result.success(receipt);
    }

    @GetMapping("/history/{businessKey}")
    public Result<ReceiptListDTO> getReceiptHistory(
            @PathVariable String businessKey,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        ReceiptListDTO history = receiptService.getReceiptHistory(businessKey, page, size);
        return Result.success(history);
    }
}
