package com.loopers.domain.common.vo;

import lombok.AllArgsConstructor;

public class MoneyExceptionMessage {

    @AllArgsConstructor
    public enum Money {
        INVALID_AMOUNT("금액은 0보다 커야 합니다.", 0_001);

        private final String message;
        private final Integer code;

        public String message() {
            return message;
        }
    }
}
