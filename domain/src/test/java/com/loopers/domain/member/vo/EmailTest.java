package com.loopers.domain.member.vo;

import com.loopers.domain.member.MemberExceptionMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class EmailTest {

    @Test
    void 유효한_이메일로_생성_성공() {
        // given
        String validEmail = "test@example.com";

        // when

        // then
        assertDoesNotThrow(() -> Email.of(validEmail));
    }

    @Test
    void 이메일_기본_형식을_준수해야_함() {
        // given
        String wrongEmail = "test#example.com";

        // when

        // then
        assertThatThrownBy(() -> Email.of(wrongEmail))
                .hasMessage(MemberExceptionMessage.Email.INVALID_FORMAT.message());
    }

    @Test
    void 이메일은_255자를_초과할_수_없음() {
        // given
        String longEmail = "a".repeat(250) + "@test.com";

        // when

        // then
        assertThatThrownBy(() -> Email.of(longEmail))
                .hasMessage(MemberExceptionMessage.Email.TOO_LONG.message());
    }
}
