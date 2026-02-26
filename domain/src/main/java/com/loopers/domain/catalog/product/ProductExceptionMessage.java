package com.loopers.domain.catalog.product;

import lombok.AllArgsConstructor;

public class ProductExceptionMessage {

    @AllArgsConstructor
    public enum Product {
        INVALID_NAME("상품명은 1자 이상 100자 이하여야 합니다.", 3_001),
        NOT_FOUND("존재하지 않는 상품입니다.", 3_002),
        ALREADY_DELETED("이미 삭제된 상품입니다.", 3_003);

        private final String message;
        private final Integer code;

        public String message() {
            return message;
        }
    }

    @AllArgsConstructor
    public enum Price {
        INVALID_PRICE("가격은 0보다 커야 합니다.", 3_101);

        private final String message;
        private final Integer code;

        public String message() {
            return message;
        }
    }

    @AllArgsConstructor
    public enum Stock {
        INVALID_STOCK("재고는 0 이상이어야 합니다.", 3_201),
        INSUFFICIENT_STOCK("재고가 부족합니다.", 3_202);

        private final String message;
        private final Integer code;

        public String message() {
            return message;
        }
    }

    @AllArgsConstructor
    public enum Quantity {
        INVALID_QUANTITY("수량은 0보다 커야 합니다.", 3_301);

        private final String message;
        private final Integer code;

        public String message() {
            return message;
        }
    }
}
