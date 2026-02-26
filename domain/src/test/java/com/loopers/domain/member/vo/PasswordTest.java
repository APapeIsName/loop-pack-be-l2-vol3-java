package com.loopers.domain.member.vo;

import com.loopers.domain.member.FakePasswordEncryptor;
import com.loopers.domain.member.MemberExceptionMessage;
import com.loopers.domain.member.PasswordEncryptor;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class PasswordTest {

    private final PasswordEncryptor encryptor = new FakePasswordEncryptor();
    private final LocalDate birthDate = LocalDate.of(2001, 2, 9);

    @Test
    void 유효한_비밀번호로_생성_성공() {
        // given
        String validPassword = "Password1!";

        // when

        // then
        assertDoesNotThrow(() -> Password.of(validPassword, birthDate, encryptor));
    }

    @Test
    void 비밀번호_길이는_8자_미만일_수_없음() {
        // given
        String shortPassword = "pap1234"; // 7글자

        // when

        // then
        assertThatThrownBy(() -> Password.of(shortPassword, birthDate, encryptor))
                .hasMessage(MemberExceptionMessage.Password.INVALID_PASSWORD_LENGTH.message());
    }

    @Test
    void 비밀번호_길이는_16자_초과일_수_없음() {
        // given
        String longPassword = "qwer1234tyui5678a"; // 17글자

        // when

        // then
        assertThatThrownBy(() -> Password.of(longPassword, birthDate, encryptor))
                .hasMessage(MemberExceptionMessage.Password.INVALID_PASSWORD_LENGTH.message());
    }

    @Test
    void 비밀번호는_영문_숫자_특수문자만_사용할_수_있음() {
        // given
        String wrongPassword = "한글password123";

        // when

        // then
        assertThatThrownBy(() -> Password.of(wrongPassword, birthDate, encryptor))
                .hasMessage(MemberExceptionMessage.Password.INVALID_PASSWORD_COMPOSITION.message());
    }

    @Test
    void 사용자_생년월일_YYYYMMDD가_비밀번호_포함_불가() {
        // given
        String wrongPassword = "pwd20010209!";

        // when

        // then
        assertThatThrownBy(() -> Password.of(wrongPassword, birthDate, encryptor))
                .hasMessage(MemberExceptionMessage.Password.PASSWORD_CONTAINS_BIRTHDATE.message());
    }

    @Test
    void 사용자_생년월일_YYMMDD가_비밀번호_포함_불가() {
        // given
        String wrongPassword = "pass010209!";

        // when

        // then
        assertThatThrownBy(() -> Password.of(wrongPassword, birthDate, encryptor))
                .hasMessage(MemberExceptionMessage.Password.PASSWORD_CONTAINS_BIRTHDATE.message());
    }
}
