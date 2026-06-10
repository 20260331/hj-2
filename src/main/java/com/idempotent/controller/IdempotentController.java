package com.idempotent.controller;

import com.idempotent.annotation.Idempotent;
import com.idempotent.dto.IdempotentHistoryDTO;
import com.idempotent.dto.IdempotentStatusDTO;
import com.idempotent.dto.OrderCreateDTO;
import com.idempotent.service.TokenService;
import com.idempotent.util.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api")
public class IdempotentController {

    @Resource
    private TokenService tokenService;

    private final Map<String, OrderCreateDTO> orderMap = new ConcurrentHashMap<>();

    @GetMapping("/token")
    public Result<String> getToken() {
        String token = tokenService.generateToken();
        return Result.success(token);
    }

    @GetMapping("/token/{businessKey}")
    public Result<String> getToken(@PathVariable String businessKey) {
        String token = tokenService.generateToken(businessKey);
        return Result.success(token);
    }

    @GetMapping("/idempotent/status/token/{token}")
    public Result<IdempotentStatusDTO> getStatusByToken(@PathVariable String token) {
        IdempotentStatusDTO status = tokenService.getStatusByToken(token);
        return Result.success(status);
    }

    @GetMapping("/idempotent/status/business/{businessKey}")
    public Result<IdempotentStatusDTO> getStatusByBusinessKey(@PathVariable String businessKey) {
        IdempotentStatusDTO status = tokenService.getStatusByBusinessKey(businessKey);
        return Result.success(status);
    }

    @GetMapping("/idempotent/history/{businessKey}")
    public Result<IdempotentHistoryDTO> getHistoryByBusinessKey(
            @PathVariable String businessKey,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        IdempotentHistoryDTO history = tokenService.getHistoryByBusinessKey(businessKey, page, size);
        return Result.success(history);
    }

    @PostMapping("/order/create")
    @Idempotent(message = "订单正在处理中，请勿重复提交")
    public Result<Map<String, Object>> createOrder(@Validated @RequestBody OrderCreateDTO orderDTO) {
        log.info("创建订单: {}", orderDTO.getOrderNo());

        if (orderMap.containsKey(orderDTO.getOrderNo())) {
            return Result.fail("订单已存在");
        }

        orderMap.put(orderDTO.getOrderNo(), orderDTO);

        Map<String, Object> result = new HashMap<>();
        result.put("orderNo", orderDTO.getOrderNo());
        result.put("status", "SUCCESS");
        result.put("message", "订单创建成功");

        return Result.success(result);
    }

    @PostMapping("/order/create-by-param")
    @Idempotent(type = com.idempotent.enums.IdempotentTypeEnum.PARAM,
            paramName = "orderNo",
            expireTime = 300,
            deleteKeyWhenFinish = false,
            message = "该订单号正在处理中，请勿重复提交")
    public Result<Map<String, Object>> createOrderByParam(@Validated @RequestBody OrderCreateDTO orderDTO) {
        log.info("根据参数幂等创建订单: {}", orderDTO.getOrderNo());

        if (orderMap.containsKey(orderDTO.getOrderNo())) {
            return Result.fail("订单已存在");
        }

        orderMap.put(orderDTO.getOrderNo(), orderDTO);

        Map<String, Object> result = new HashMap<>();
        result.put("orderNo", orderDTO.getOrderNo());
        result.put("status", "SUCCESS");
        result.put("message", "订单创建成功");

        return Result.success(result);
    }

    @PostMapping("/payment/submit")
    @Idempotent(message = "支付请求正在处理中，请勿重复支付")
    public Result<Map<String, Object>> submitPayment(@RequestParam String orderNo,
                                                     @RequestParam String payMethod) {
        log.info("提交支付: orderNo={}, payMethod={}", orderNo, payMethod);

        Map<String, Object> result = new HashMap<>();
        result.put("orderNo", orderNo);
        result.put("payMethod", payMethod);
        result.put("status", "PROCESSING");
        result.put("message", "支付处理中");

        return Result.success(result);
    }

    @PostMapping("/message/consume")
    @Idempotent(type = com.idempotent.enums.IdempotentTypeEnum.PARAM,
            paramName = "messageId",
            expireTime = 86400,
            deleteKeyWhenFinish = false,
            message = "消息已消费，请勿重复消费")
    public Result<String> consumeMessage(@RequestParam String messageId,
                                         @RequestParam String content) {
        log.info("消费消息: messageId={}, content={}", messageId, content);

        return Result.success("消息消费成功");
    }

    @GetMapping("/order/{orderNo}")
    public Result<OrderCreateDTO> getOrder(@PathVariable String orderNo) {
        OrderCreateDTO order = orderMap.get(orderNo);
        if (order == null) {
            return Result.fail("订单不存在");
        }
        return Result.success(order);
    }
}
