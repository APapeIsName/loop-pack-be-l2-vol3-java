package com.loopers.domain.payment;

public class PaymentFixture {

    public static final Long DEFAULT_ORDER_ID = 1L;
    public static final Long DEFAULT_MEMBER_ID = 10L;
    public static final CardType DEFAULT_CARD_TYPE = CardType.SAMSUNG;
    public static final String DEFAULT_CARD_NO = "1234-5678-9012-3456";
    public static final long DEFAULT_AMOUNT = 50000L;

    public static Payment create() {
        return Payment.request(DEFAULT_ORDER_ID, DEFAULT_MEMBER_ID, DEFAULT_CARD_TYPE, DEFAULT_CARD_NO, DEFAULT_AMOUNT);
    }

    public static Payment create(Long orderId, Long memberId) {
        return Payment.request(orderId, memberId, DEFAULT_CARD_TYPE, DEFAULT_CARD_NO, DEFAULT_AMOUNT);
    }

    public static Payment create(Long orderId, Long memberId, long amount) {
        return Payment.request(orderId, memberId, DEFAULT_CARD_TYPE, DEFAULT_CARD_NO, amount);
    }
}
