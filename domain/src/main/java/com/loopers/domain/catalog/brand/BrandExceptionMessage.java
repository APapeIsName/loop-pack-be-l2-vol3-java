package com.loopers.domain.catalog.brand;

import lombok.AllArgsConstructor;

public class BrandExceptionMessage {

    @AllArgsConstructor
    public enum Brand {
        INVALID_NAME("브랜드명은 1자 이상 100자 이하여야 합니다.", 2_001),
        DUPLICATE_NAME("이미 존재하는 브랜드명입니다.", 2_002),
        NOT_FOUND("존재하지 않는 브랜드입니다.", 2_003),
        ALREADY_DELETED("이미 삭제된 브랜드입니다.", 2_004);

        private final String message;
        private final Integer code;

        public String message() {
            return message;
        }
    }
}
