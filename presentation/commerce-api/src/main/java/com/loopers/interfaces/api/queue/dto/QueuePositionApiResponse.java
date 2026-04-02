package com.loopers.interfaces.api.queue.dto;

import com.loopers.application.service.dto.QueuePositionInfo;

public record QueuePositionApiResponse(
        Long productId,
        long position,
        long totalWaiting,
        long estimatedWaitSeconds,
        int suggestedPollingIntervalMs,
        boolean hasToken,
        String token
) {
    public static QueuePositionApiResponse from(QueuePositionInfo info) {
        return new QueuePositionApiResponse(
                info.productId(),
                info.position(),
                info.totalWaiting(),
                info.estimatedWaitSeconds(),
                info.suggestedPollingIntervalMs(),
                info.hasToken(),
                info.token()
        );
    }
}
