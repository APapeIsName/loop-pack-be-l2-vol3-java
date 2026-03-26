package com.loopers.infrastructure.metrics;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EventHandledRepository extends JpaRepository<EventHandled, Long> {
}
