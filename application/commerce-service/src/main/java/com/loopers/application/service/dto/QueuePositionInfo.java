package com.loopers.application.service.dto;

public record QueuePositionInfo(
        Long productId,
        long position,
        long totalWaiting,
        long estimatedWaitSeconds,
        int suggestedPollingIntervalMs,
        boolean hasToken,
        String token
) {

    public static QueuePositionInfo waiting(Long productId, long position, long totalWaiting,
                                            long estimatedWaitSeconds, int suggestedPollingIntervalMs) {
        return new QueuePositionInfo(productId, position, totalWaiting,
                estimatedWaitSeconds, suggestedPollingIntervalMs, false, null);
    }

    public static QueuePositionInfo ready(Long productId, String token) {
        return new QueuePositionInfo(productId, 0, 0, 0, 0, true, token);
    }
}
