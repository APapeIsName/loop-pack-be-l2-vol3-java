package com.loopers.domain.payment;

import lombok.AllArgsConstructor;

public class PaymentExceptionMessage {

    @AllArgsConstructor
    public enum Payment {
        NOT_FOUND("존재하지 않는 결제입니다.", 5_001),
        ALREADY_PROCESSED("이미 처리된 결제입니다.", 5_002),
        NOT_OWNER("본인의 결제가 아닙니다.", 5_003),
        DUPLICATE_PAYMENT("이미 결제가 진행 중인 주문입니다.", 5_004),
        NOT_REQUESTED("PG 접수 전 상태가 아닙니다.", 5_006);

        private final String message;
        private final Integer code;

        public String message() {
            return message;
        }
    }
}
