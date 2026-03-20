package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    @Test
    void 결제_요청_생성_시_PENDING_상태() {
        // when
        Payment payment = Payment.request(1L, 10L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000);

        // then
        assertThat(payment.isPending()).isTrue();
    }

    @Test
    void 트랜잭션키_할당() {
        // given
        Payment payment = Payment.request(1L, 10L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000);

        // when
        payment.assignTransactionKey("20250816:TR:abc123");

        // then
        assertThat(payment.getTransactionKey()).isEqualTo("20250816:TR:abc123");
    }

    @Test
    void 결제_승인() {
        // given
        Payment payment = Payment.request(1L, 10L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000);

        // when
        payment.approve();

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void 결제_실패() {
        // given
        Payment payment = Payment.request(1L, 10L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000);

        // when
        payment.fail("한도 초과");

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void 결제_실패_시_사유_저장() {
        // given
        Payment payment = Payment.request(1L, 10L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000);

        // when
        payment.fail("한도 초과");

        // then
        assertThat(payment.getFailureReason()).isEqualTo("한도 초과");
    }

    @Test
    void 이미_승인된_결제_재승인_예외() {
        // given
        Payment payment = Payment.request(1L, 10L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000);
        payment.approve();

        // when & then
        assertThatThrownBy(payment::approve)
                .isInstanceOf(CoreException.class)
                .hasMessage(PaymentExceptionMessage.Payment.ALREADY_PROCESSED.message());
    }

    @Test
    void 이미_실패한_결제_승인_예외() {
        // given
        Payment payment = Payment.request(1L, 10L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000);
        payment.fail("잘못된 카드");

        // when & then
        assertThatThrownBy(payment::approve)
                .isInstanceOf(CoreException.class)
                .hasMessage(PaymentExceptionMessage.Payment.ALREADY_PROCESSED.message());
    }

    @Test
    void 본인_결제_확인() {
        // given
        Payment payment = Payment.request(1L, 10L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000);

        // when & then
        assertThat(payment.isOwnedBy(10L)).isTrue();
    }

    @Test
    void 본인_아닌_결제_확인() {
        // given
        Payment payment = Payment.request(1L, 10L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000);

        // when & then
        assertThat(payment.isOwnedBy(99L)).isFalse();
    }
}
