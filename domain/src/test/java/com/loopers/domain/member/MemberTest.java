package com.loopers.domain.member;

import com.loopers.domain.member.vo.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class MemberTest {

    private final PasswordEncryptor encryptor = new FakePasswordEncryptor();

    private static final LocalDate VALID_BIRTH_DATE = LocalDate.of(2001, 2, 9);

    private LoginId validLoginId() {
        return LoginId.of("hello1234");
    }

    private Password validPassword() {
        return Password.of("Password1!", VALID_BIRTH_DATE, encryptor);
    }

    private MemberName validName() {
        return MemberName.of("홍길동");
    }

    private Email validEmail() {
        return Email.of("test@example.com");
    }

    @Test
    void 회원가입_성공() {
        // given

        // when

        // then
        assertDoesNotThrow(() ->
                Member.register(validLoginId(), validPassword(), validName(), VALID_BIRTH_DATE, validEmail())
        );
    }

    @Nested
    class 생년월일_유효성_검증 {

        @Test
        void 미래_날짜는_생년월일_등록_불가() {
            // given
            LocalDate futureDate = LocalDate.now().plusDays(1);

            // when

            // then
            assertThatThrownBy(() -> Member.register(validLoginId(), validPassword(), validName(), futureDate, validEmail()))
                    .hasMessage(MemberExceptionMessage.BirthDate.CANNOT_BE_FUTURE.message());
        }
    }

    @Nested
    class 비밀번호_수정_정책_검증 {

        @Test
        void 새_비밀번호가_현재_비밀번호와_같으면_예외() {
            // given
            String currentPassword = "oldPassword1!";
            Password password = Password.of(currentPassword, VALID_BIRTH_DATE, encryptor);
            Member member = Member.register(validLoginId(), password, validName(), VALID_BIRTH_DATE, validEmail());

            // when & then
            assertThatThrownBy(() -> member.updatePassword(currentPassword, encryptor))
                    .hasMessage(MemberExceptionMessage.Password.PASSWORD_CANNOT_BE_SAME_AS_CURRENT.message());
        }

        @Test
        void 새_비밀번호에_생년월일이_포함되면_예외() {
            // given
            Member member = Member.register(validLoginId(), validPassword(), validName(), VALID_BIRTH_DATE, validEmail());

            // when & then
            assertThatThrownBy(() -> member.updatePassword("pass20010209!", encryptor))
                    .hasMessage(MemberExceptionMessage.Password.PASSWORD_CONTAINS_BIRTHDATE.message());
        }
    }
}
