package com.loopers.domain.member.vo;

import com.loopers.domain.member.MemberExceptionMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class LoginIdTest {

    @Test
    void 유효한_아이디로_생성_성공() {
        // given
        String validId = "hello1234";

        // when

        // then
        assertDoesNotThrow(() -> LoginId.of(validId));
    }

    @Test
    void 아이디는_영문이_아닌_한글이_들어갈_수_없음() {
        // given
        String wrongId = "한글입slek";

        // when

        // then
        assertThatThrownBy(() -> LoginId.of(wrongId))
                .hasMessage(MemberExceptionMessage.LoginId.INVALID_ID_FORMAT.message());
    }

    @Test
    void 아이디는_영문이_아닌_특수문자가_들어갈_수_없음() {
        // given
        String wrongId = "@apgl!#";

        // when

        // then
        assertThatThrownBy(() -> LoginId.of(wrongId))
                .hasMessage(MemberExceptionMessage.LoginId.INVALID_ID_FORMAT.message());
    }

    @Test
    void 아이디는_숫자만_존재할_수_없음() {
        // given
        String wrongId = "12345678";

        // when

        // then
        assertThatThrownBy(() -> LoginId.of(wrongId))
                .hasMessage(MemberExceptionMessage.LoginId.INVALID_ID_NUMERIC_ONLY.message());
    }

    @Test
    void 아이디의_길이_6자_미만_불가() {
        // given
        String wrongId = "ap245";

        // when

        // then
        assertThatThrownBy(() -> LoginId.of(wrongId))
                .hasMessage(MemberExceptionMessage.LoginId.INVALID_ID_LENGTH.message());
    }

    @Test
    void 아이디의_길이_20자_초과_불가() {
        // given
        String wrongId = "apapeisname1234ppap56"; // 21글자

        // when

        // then
        assertThatThrownBy(() -> LoginId.of(wrongId))
                .hasMessage(MemberExceptionMessage.LoginId.INVALID_ID_LENGTH.message());
    }
}
