package com.loopers.domain.payment;

import lombok.AllArgsConstructor;

public class PaymentExceptionMessage {

    @AllArgsConstructor
    public enum Payment {
        NOT_FOUND("존재하지 않는 결제입니다.", 5_001),
        ALREADY_PROCESSED("이미 처리된 결제입니다.", 5_002),
        NOT_OWNER("본인의 결제가 아닙니다.", 5_003),
        ORDER_NOT_ACCEPTED("수락된 주문만 결제할 수 있습니다.", 5_004),
        DUPLICATE_PAYMENT("이미 결제가 진행 중인 주문입니다.", 5_005);

        private final String message;
        private final Integer code;

        public String message() {
            return message;
        }
    }
}
