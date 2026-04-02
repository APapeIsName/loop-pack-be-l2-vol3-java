package com.loopers.application.service;

import com.loopers.application.service.dto.OrderCreateCommand;
import com.loopers.application.service.dto.OrderInfo;
import com.loopers.application.service.dto.OrderLineRequest;
import com.loopers.application.service.dto.QueueEnterCommand;
import com.loopers.application.service.dto.QueuePositionInfo;
import com.loopers.domain.queue.QueueProductRepository;
import com.loopers.domain.queue.WaitingQueueRepository;
import com.loopers.domain.queue.QueueExceptionMessage;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class OrderQueueService {

    private final long maxCapacity;
    private final long tokenTtlSeconds;
    private final int batchSize;
    private final int throughputPerSecond;
    private final int displayThroughputPerSecond;

    private final WaitingQueueRepository waitingQueueRepository;
    private final QueueProductRepository queueProductRepository;
    private final OrderService orderService;

    public OrderQueueService(
            @Value("${queue.max-capacity:10000}") long maxCapacity,
            @Value("${queue.token-ttl-seconds:300}") long tokenTtlSeconds,
            @Value("${queue.batch-size:10}") int batchSize,
            @Value("${queue.throughput-per-second:100}") int throughputPerSecond,
            @Value("${queue.display-throughput-per-second:80}") int displayThroughputPerSecond,
            WaitingQueueRepository waitingQueueRepository,
            QueueProductRepository queueProductRepository,
            OrderService orderService
    ) {
        this.maxCapacity = maxCapacity;
        this.tokenTtlSeconds = tokenTtlSeconds;
        this.batchSize = batchSize;
        this.throughputPerSecond = throughputPerSecond;
        this.displayThroughputPerSecond = displayThroughputPerSecond;
        this.waitingQueueRepository = waitingQueueRepository;
        this.queueProductRepository = queueProductRepository;
        this.orderService = orderService;
    }

    public QueuePositionInfo enterQueue(QueueEnterCommand command) {
        validateQueueActive(command.productId());

        long position = waitingQueueRepository.enqueue(
                command.productId(), command.memberId(), maxCapacity);

        if (position == -1) {
            throw new CoreException(ErrorType.CONFLICT,
                    QueueExceptionMessage.Queue.QUEUE_FULL.message());
        }

        long totalWaiting = waitingQueueRepository.getTotalCount(command.productId());
        long estimatedWaitSeconds = calculateEstimatedWait(position);
        int pollingInterval = calculatePollingInterval(position);

        return QueuePositionInfo.waiting(
                command.productId(), position, totalWaiting, estimatedWaitSeconds, pollingInterval);
    }

    public QueuePositionInfo getPosition(Long productId, Long memberId) {
        if (queueProductRepository.isSoldOut(productId)) {
            throw new CoreException(ErrorType.CONFLICT,
                    QueueExceptionMessage.Queue.SOLD_OUT.message());
        }

        if (waitingQueueRepository.hasToken(productId, memberId)) {
            String token = waitingQueueRepository.getToken(productId, memberId);
            return QueuePositionInfo.ready(productId, token);
        }

        Long position = waitingQueueRepository.getPosition(productId, memberId);
        if (position == null) {
            throw new CoreException(ErrorType.NOT_FOUND,
                    QueueExceptionMessage.Queue.NOT_IN_QUEUE.message());
        }

        long totalWaiting = waitingQueueRepository.getTotalCount(productId);
        long estimatedWaitSeconds = calculateEstimatedWait(position);
        int pollingInterval = calculatePollingInterval(position);

        return QueuePositionInfo.waiting(
                productId, position, totalWaiting, estimatedWaitSeconds, pollingInterval);
    }

    public void exitQueue(Long productId, Long memberId) {
        waitingQueueRepository.dequeue(productId, memberId);
    }

    public void processAllQueues() {
        Set<Long> activeProductIds = queueProductRepository.getActiveProductIds();
        if (activeProductIds.isEmpty()) {
            return;
        }
        int perProductBatchSize = Math.max(1, batchSize / activeProductIds.size());
        for (Long productId : activeProductIds) {
            processQueue(productId, perProductBatchSize);
        }
    }

    public boolean isQueueActiveProduct(Long productId) {
        return queueProductRepository.isActiveProduct(productId);
    }

    public void validateTokenForOrder(Long productId, Long memberId, String token) {
        if (token == null || token.isBlank()) {
            throw new CoreException(ErrorType.FORBIDDEN,
                    QueueExceptionMessage.Token.NO_TOKEN.message());
        }

        boolean consumed = waitingQueueRepository.validateAndConsumeToken(productId, memberId, token);
        if (!consumed) {
            throw new CoreException(ErrorType.FORBIDDEN,
                    QueueExceptionMessage.Token.INVALID_TOKEN.message());
        }
    }

    public OrderInfo createOrderWithQueue(OrderCreateCommand command, String entryToken) {
        Long queueProductId = findQueueProductId(command);
        if (queueProductId != null) {
            validateTokenForOrder(queueProductId, command.memberId(), entryToken);
        }

        return orderService.create(command);
    }

    private Long findQueueProductId(OrderCreateCommand command) {
        for (OrderLineRequest line : command.orderLines()) {
            if (isQueueActiveProduct(line.productId())) {
                return line.productId();
            }
        }
        return null;
    }

    public void activateQueue(Long productId) {
        queueProductRepository.registerActiveProduct(productId);
        log.info("대기열 활성화: productId={}", productId);
    }

    public void deactivateQueue(Long productId) {
        queueProductRepository.unregisterActiveProduct(productId);
        log.info("대기열 비활성화: productId={}", productId);
    }

    public void markSoldOut(Long productId) {
        queueProductRepository.markSoldOut(productId);
        log.info("매진 처리: productId={}", productId);
    }

    public void clearSoldOut(Long productId) {
        queueProductRepository.clearSoldOut(productId);
        log.info("매진 해제: productId={}", productId);
    }

    private void processQueue(Long productId, int size) {
        if (queueProductRepository.isSoldOut(productId)) {
            return;
        }

        List<Long> memberIds = waitingQueueRepository.popFront(productId, size);
        if (memberIds.isEmpty()) {
            return;
        }

        for (Long memberId : memberIds) {
            String token = UUID.randomUUID().toString();
            waitingQueueRepository.issueToken(productId, memberId, token, tokenTtlSeconds);
            log.debug("토큰 발급: productId={}, memberId={}", productId, memberId);
        }
    }

    private long calculateEstimatedWait(long position) {
        if (position <= 0) return 0;
        return Math.max(1, position / displayThroughputPerSecond);
    }

    private int calculatePollingInterval(long position) {
        long estimatedWaitMs = (position * 1000L) / displayThroughputPerSecond;
        int pollingMs = (int) (estimatedWaitMs / 10);
        return Math.max(1000, Math.min(5000, pollingMs));
    }

    private void validateQueueActive(Long productId) {
        if (!isQueueActiveProduct(productId)) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    QueueExceptionMessage.Queue.QUEUE_NOT_ACTIVE.message());
        }
    }
}
