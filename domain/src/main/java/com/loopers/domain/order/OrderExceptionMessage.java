package com.loopers.domain.order;

import lombok.AllArgsConstructor;

public class OrderExceptionMessage {

    @AllArgsConstructor
    public enum Order {
        NOT_FOUND("존재하지 않는 주문입니다.", 4_001),
        NOT_OWNER("본인의 주문이 아닙니다.", 4_002),
        EMPTY_ORDER_LINES("주문할 상품을 선택해주세요.", 4_003),
        DUPLICATE_PRODUCT("동일한 상품이 중복으로 포함되어 있습니다.", 4_004),
        NOT_ACCEPTED("수락된 주문만 결제할 수 있습니다.", 4_005),
        NOT_CANCELLABLE("취소할 수 없는 주문입니다.", 4_006);

        private final String message;
        private final Integer code;

        public String message() {
            return message;
        }
    }
}
