package com.loopers.interfaces.api.payment;

import com.loopers.application.service.PaymentService;
import com.loopers.interfaces.api.payment.dto.PaymentCallbackApiRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class PaymentCallbackController {

    private final PaymentService paymentService;

    @PostMapping("/api/v1/payments/callback")
    public void handleCallback(@RequestBody PaymentCallbackApiRequest request) {
        try {
            paymentService.handleCallback(request.toCommand());
        } catch (Exception e) {
            log.warn("콜백 처리 중 예외 발생 — transactionKey={}, error={}",
                    request.transactionKey(), e.getMessage());
        }
    }
}
