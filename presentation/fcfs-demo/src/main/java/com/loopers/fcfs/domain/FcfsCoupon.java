package com.loopers.fcfs.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "fcfs_coupon")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FcfsCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int totalQuantity;

    @Column(nullable = false)
    private int issuedCount;

    private LocalDateTime openAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private FcfsCoupon(String name, int totalQuantity, LocalDateTime openAt) {
        this.name = name;
        this.totalQuantity = totalQuantity;
        this.issuedCount = 0;
        this.openAt = openAt;
        this.createdAt = LocalDateTime.now();
    }

    public static FcfsCoupon create(String name, int totalQuantity, LocalDateTime openAt) {
        return new FcfsCoupon(name, totalQuantity, openAt);
    }

    public boolean isOpen() {
        return openAt != null && LocalDateTime.now().isAfter(openAt);
    }

    public boolean isSoldOut() {
        return issuedCount >= totalQuantity;
    }

    public void incrementIssuedCount() {
        this.issuedCount++;
    }

    public void reset() {
        this.issuedCount = 0;
    }

    public void setOpenAt(LocalDateTime openAt) {
        this.openAt = openAt;
    }
}
