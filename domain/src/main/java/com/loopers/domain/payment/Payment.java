package com.loopers.domain.payment;

import com.loopers.domain.BaseTimeEntity;
import com.loopers.domain.common.vo.Money;
import com.loopers.support.error.CoreException;
import java.time.ZonedDateTime;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "payment")
public class Payment extends BaseTimeEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "transaction_key")
    private String transactionKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false)
    private CardType cardType;

    @Column(name = "card_no", nullable = false)
    private String cardNo;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "amount", nullable = false))
    private Money amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    private Payment(Long orderId, Long memberId, CardType cardType, String cardNo, Money amount) {
        this.orderId = orderId;
        this.memberId = memberId;
        this.cardType = cardType;
        this.cardNo = cardNo;
        this.amount = amount;
        this.status = PaymentStatus.REQUESTED;
    }

    public static Payment request(Long orderId, Long memberId, CardType cardType, String cardNo, long amount) {
        return new Payment(orderId, memberId, cardType, cardNo, Money.of(amount));
    }

    public void pend(String transactionKey) {
        validateRequested();
        this.transactionKey = transactionKey;
        this.status = PaymentStatus.PENDING;
    }

    public void approve() {
        validatePending();
        this.status = PaymentStatus.APPROVED;
    }

    public void fail(String reason) {
        validateInProgress();
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
    }

    public boolean isRequested() {
        return this.status.isRequested();
    }

    public boolean isPending() {
        return this.status.isPending();
    }

    public boolean hasTransactionKey() {
        return this.transactionKey != null;
    }

    public boolean isCompleted() {
        return this.status.isCompleted();
    }

    public boolean isCreatedBefore(ZonedDateTime threshold) {
        return this.getCreatedAt().isBefore(threshold);
    }

    public boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }

    private void validateRequested() {
        if (!this.status.isRequested()) {
            throw new CoreException(ErrorType.CONFLICT,
                    PaymentExceptionMessage.Payment.NOT_REQUESTED.message());
        }
    }

    private void validatePending() {
        if (!this.status.isPending()) {
            throw new CoreException(ErrorType.CONFLICT,
                    PaymentExceptionMessage.Payment.ALREADY_PROCESSED.message());
        }
    }

    private void validateInProgress() {
        if (this.status.isCompleted()) {
            throw new CoreException(ErrorType.CONFLICT,
                    PaymentExceptionMessage.Payment.ALREADY_PROCESSED.message());
        }
    }
}
