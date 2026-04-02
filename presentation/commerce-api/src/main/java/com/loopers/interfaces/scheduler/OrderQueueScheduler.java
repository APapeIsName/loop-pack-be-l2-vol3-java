package com.loopers.interfaces.scheduler;

import com.loopers.application.service.OrderQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderQueueScheduler {

    private final OrderQueueService orderQueueService;

    @Scheduled(fixedDelayString = "${queue.scheduler-interval:100}")
    public void processQueues() {
        try {
            orderQueueService.processAllQueues();
        } catch (Exception e) {
            log.error("대기열 처리 실패", e);
        }
    }
}
