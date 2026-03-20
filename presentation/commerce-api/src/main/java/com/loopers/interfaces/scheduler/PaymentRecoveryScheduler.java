package com.loopers.interfaces.scheduler;

import com.loopers.application.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRecoveryScheduler {

    private final PaymentService paymentService;

    @Scheduled(fixedDelay = 60_000)
    public void recover() {
        log.info("PENDING 결제 복구 시작");
        paymentService.reconcileAll();
        log.info("PENDING 결제 복구 완료");
    }
}
