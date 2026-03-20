package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    @Test
    void 결제_요청_생성_시_REQUESTED_상태() {
        // when
        Payment payment = Payment.request(1L, 10L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000);

        // then
        assertThat(payment.isRequested()).isTrue();
    }

    @Test
    void PG_접수_시_PENDING_전이_및_트랜잭션키_할당() {
        // given
        Payment payment = Payment.request(1L, 10L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000);

        // when
        payment.pend("20250816:TR:abc123");

        // then
        assertThat(payment.isPending()).isTrue();
    }

    @Test
    void PG_접수_시_트랜잭션키_저장() {
        // given
        Payment payment = Payment.request(1L, 10L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000);

        // when
        payment.pend("20250816:TR:abc123");

        // then
        assertThat(payment.getTransactionKey()).isEqualTo("20250816:TR:abc123");
    }

    @Test
    void 결제_승인() {
        // given
        Payment payment = Payment.request(1L, 10L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000);
        payment.pend("TR:key");

        // when
        payment.approve();

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void REQUESTED에서_직접_승인_시_예외() {
        // given
        Payment payment = Payment.request(1L, 10L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000);

        // when & then
        assertThatThrownBy(payment::approve)
                .isInstanceOf(CoreException.class)
                .hasMessage(PaymentExceptionMessage.Payment.ALREADY_PROCESSED.message());
    }

    @Test
    void REQUESTED에서_실패_처리_가능() {
        // given
        Payment payment = Payment.request(1L, 10L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000);

        // when
        payment.fail("PG 거절");

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void PENDING에서_실패_처리_가능() {
        // given
        Payment payment = Payment.request(1L, 10L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000);
        payment.pend("TR:key");

        // when
        payment.fail("콜백 실패");

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
        payment.pend("TR:key");
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
    void 이미_PENDING인_결제에_중복_pend_시_예외() {
        // given
        Payment payment = Payment.request(1L, 10L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000);
        payment.pend("TR:first");

        // when & then
        assertThatThrownBy(() -> payment.pend("TR:second"))
                .isInstanceOf(CoreException.class)
                .hasMessage(PaymentExceptionMessage.Payment.NOT_REQUESTED.message());
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
