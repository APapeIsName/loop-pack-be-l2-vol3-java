package com.loopers.domain.like;

import lombok.AllArgsConstructor;

public class LikeExceptionMessage {

    @AllArgsConstructor
    public enum Like {
        ALREADY_LIKED("이미 좋아요한 상품입니다.", 5_001),
        NOT_LIKED("좋아요하지 않은 상품입니다.", 5_002);

        private final String message;
        private final Integer code;

        public String message() {
            return message;
        }
    }
}
