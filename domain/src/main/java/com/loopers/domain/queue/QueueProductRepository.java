package com.loopers.domain.queue;

import java.util.Set;

public interface QueueProductRepository {

    void registerActiveProduct(Long productId);

    void unregisterActiveProduct(Long productId);

    Set<Long> getActiveProductIds();

    boolean isActiveProduct(Long productId);

    void markSoldOut(Long productId);

    void clearSoldOut(Long productId);

    boolean isSoldOut(Long productId);
}
