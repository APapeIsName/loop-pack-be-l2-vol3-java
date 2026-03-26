package com.loopers.infrastructure.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "event_handled")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventHandled {

    @Id
    @Column(nullable = false)
    private Long eventId;

    @Column(nullable = false)
    private LocalDateTime handledAt;

    private EventHandled(Long eventId) {
        this.eventId = eventId;
        this.handledAt = LocalDateTime.now();
    }

    public static EventHandled of(Long eventId) {
        return new EventHandled(eventId);
    }
}
