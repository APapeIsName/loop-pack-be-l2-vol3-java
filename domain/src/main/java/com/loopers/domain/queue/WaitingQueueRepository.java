package com.loopers.domain.queue;

import java.util.List;

public interface WaitingQueueRepository {

    long enqueue(Long productId, Long memberId, long maxCapacity);

    Long getPosition(Long productId, Long memberId);

    long getTotalCount(Long productId);

    List<Long> popFront(Long productId, int count);

    boolean dequeue(Long productId, Long memberId);

    void issueToken(Long productId, Long memberId, String token, long ttlSeconds);

    boolean hasToken(Long productId, Long memberId);

    String getToken(Long productId, Long memberId);

    boolean validateAndConsumeToken(Long productId, Long memberId, String token);
}
