package com.loopers.domain.queue;

import lombok.AllArgsConstructor;

public class QueueExceptionMessage {

    @AllArgsConstructor
    public enum Queue {
        QUEUE_FULL("대기열이 가득 찼습니다.", 8_001),
        ALREADY_IN_QUEUE("이미 대기열에 진입한 상태입니다.", 8_002),
        NOT_IN_QUEUE("대기열에 존재하지 않습니다.", 8_003),
        QUEUE_NOT_ACTIVE("해당 상품은 대기열이 활성화되지 않았습니다.", 8_004),
        SOLD_OUT("해당 상품은 매진되었습니다.", 8_005);

        private final String message;
        private final Integer code;

        public String message() {
            return message;
        }
    }

    @AllArgsConstructor
    public enum Token {
        NO_TOKEN("입장 토큰이 필요합니다.", 8_101),
        INVALID_TOKEN("유효하지 않은 입장 토큰입니다.", 8_102);

        private final String message;
        private final Integer code;

        public String message() {
            return message;
        }
    }
}
