package com.loopers.domain.coupon;

import lombok.AllArgsConstructor;

public class CouponExceptionMessage {

    @AllArgsConstructor
    public enum Coupon {
        NOT_FOUND("존재하지 않는 쿠폰입니다.", 6_001),
        ALREADY_EXPIRED("이미 만료된 쿠폰입니다.", 6_002),
        ALREADY_DELETED("이미 삭제된 쿠폰입니다.", 6_003),
        INVALID_DISCOUNT_VALUE("할인 값이 유효하지 않습니다.", 6_004),
        MIN_ORDER_AMOUNT_NOT_MET("최소 주문 금액을 충족하지 않습니다.", 6_005);

        private final String message;
        private final Integer code;

        public String message() {
            return message;
        }
    }

    @AllArgsConstructor
    public enum IssuedCoupon {
        NOT_FOUND("존재하지 않는 발급 쿠폰입니다.", 6_101),
        NOT_AVAILABLE("사용할 수 없는 쿠폰입니다.", 6_102),
        NOT_OWNER("본인의 쿠폰이 아닙니다.", 6_103);

        private final String message;
        private final Integer code;

        public String message() {
            return message;
        }
    }
}
