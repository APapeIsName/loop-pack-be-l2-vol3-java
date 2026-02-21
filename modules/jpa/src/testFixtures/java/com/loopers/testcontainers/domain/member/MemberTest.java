package com.loopers.testcontainers.domain.member;

import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberExceptionMessage;
import com.loopers.utils.PasswordEncryptor;
import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.LocalDateAssert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Member 도메인 엔티티 유효성 검증 테스트
 *
 * 검증 대상:
 * - 로그인 ID: 영문/숫자만 허용, 6~20글자
 * - 비밀번호: 8~16자, 영문 대소문자 + 숫자 + 특수문자 조합
 * - 이름: 한글/영어 허용, 2~40글자
 * - 이메일: RFC 5321 표준 준수
 */
@DisplayName("Member 도메인 유효성 검증 테스트")
class MemberTest {

    // 테스트용 유효한 기본값
    private static final String VALID_LOGIN_ID = "hello1234";
    private static final String VALID_PASSWORD = "Password1!";
    private static final String VALID_NAME = "홍길동";
    private static final LocalDate VALID_BIRTH_DATE = LocalDate.of(2001, 2, 9);
    private static final String VALID_EMAIL = "test@example.com";

    @Test
    public void 회원가입_성공() throws Exception {
        //given

        //when

        //then
        assertDoesNotThrow(() ->
                Member.register(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL)
        );

    }

    @DisplayName("회원가입 시에 ID 검증")
    @Nested
    class LoginIdValidation {

        // 1. 아이디는 영문과 숫자가 합쳐진 경우 허용하며, 중복 가입할 수 없으며, 6~20글자여야 함.
        // 1-1. 아이디는 영문이 들어가야 함.
        // 1-1-1. 아이디는 영문이 아닌 한글이 들어갈 수 없음.
        // 1-1-2. 아이디는 영문이 아닌 특수 문자가 들어갈 수 없음.
        // 1-2. 아이디는 숫자만 존재할 수 없음.
        // 1-3. 아이디는 중복 가입할 수 없음. ->
        // 1-3-1. 아이디가 중복인 경우 예외 메시지를 띄워야 함.
        // 1-4. 아이디의 길이는 6~20글자여야 함.
        // 1-4-1. 아이디의 길이는 6글자 미만일 수 없음.
        // 1-4-2. 아이디의 길이는 20글자 초과일 수 없음.

        // 1-1-1
        @Test
        public void 아이디는_영문이_아닌_한글이_들어갈_수_없음() throws Exception {
            //given
            String wrongId = "한글입slek";

            //when
            
            //then
            throwIfWrongIdInput(wrongId)
                    .hasMessage(MemberExceptionMessage.LoginId.INVALID_ID_FORMAT.message());

        }

        // 1-1-2
        @Test
        public void 아이디는_영문이_아닌_특수문자가_들어갈_수_없음() throws Exception
        {
            //given
            String wrongId = "@apgl!#";

            //when

            //then
            throwIfWrongIdInput(wrongId)
                    .hasMessage(MemberExceptionMessage.LoginId.INVALID_ID_FORMAT.message());
        }

        // 1-2
        @Test
        public void 아이디는_숫자만_존재할_수_없음() throws Exception {
            //given
            String wrongId = "12345678";

            //when

            //then
            throwIfWrongIdInput(wrongId)
                    .hasMessage(MemberExceptionMessage.LoginId.INVALID_ID_NUMERIC_ONLY.message());
        }

        // 1-3 -> 레포지토리 가져오니까 서비스의 통합 테스트
        @Test
        public void 아이디는_중복_가입할_수_없음() throws Exception {
            //given

            //when

            //then

        }

        // 1-4-1
        @Test
        public void 아이디의_길이_6자_미만_불가() throws Exception {
            //given
            String wrongId = "ap245";

            //when

            //then
            throwIfWrongIdInput(wrongId)
                    .hasMessage(MemberExceptionMessage.LoginId.INVALID_ID_LENGTH.message());
        }

        // 1-4-2
        @Test
        public void 아이디의_길이_20자_초과_불가() throws Exception {
            //given
            String wrongId = "apapeisname1234ppap56"; // 21글자

            //when

            //then
            throwIfWrongIdInput(wrongId)
                    .hasMessage(MemberExceptionMessage.LoginId.INVALID_ID_LENGTH.message());
        }

        private AbstractThrowableAssert<?, ? extends Throwable> throwIfWrongIdInput(String wrongId) {
            return assertThatThrownBy(() -> Member.register(wrongId, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * [비밀번호 보안 정책 - Zero-Birthdate Policy 기반]
     * * 1. 기본 형식 검증 (Basic Format)
     * - 1-1. 길이는 8자 이상 16자 이하여야 함.
     * - 1-1-1. 길이는 8자 미만일 수 없음
     * - 1-1-2. 길이는 16자 초과일 수 없음
     * - 1-2. 영문 대문자, 영문 소문자, 숫자, 특수문자가 최소 1개 이상 포함된 조합이어야 함.
     * * 2. 생년월일 포함 금지 규칙 (Zero-Birthdate Policy)
     * - 2-1. 사용자의 생년월일(YYYYMMDD)이 비밀번호 문자열 내에 포함될 수 없음. (ex: "pass19950520!")
     * - 2-2. 사용자의 생년월일(YYMMDD)이 비밀번호 문자열 내에 포함될 수 없음. (ex: "950520pass#")
     * * 3. 수정 시 정책 (Update Policy)
     * - 3-1. 현재 사용 중인 비밀번호(암호화 전 원문 기준)와 동일한 비밀번호로 변경 불가. -> Service, 통합
     * * 4. 보안 전제 조건
     * - 4-1. DB 저장 전 반드시 단방향 해시 암호화 과정을 거쳐야 함.
     */

    @DisplayName("비밀번호 형식 검증")
    @Nested
    class PasswordFormatValidation {

        // 1-1-1
        @Test
        public void 비밀번호_길이는_8자_미만일_수_없음() throws Exception {
            //given
            String wrongPassword = "pap1234"; // 7글자

            //when

            //then
            throwIfWrongPasswordInput(wrongPassword)
                    .hasMessage(MemberExceptionMessage.Password.INVALID_PASSWORD_LENGTH.message());
        }

        // 1-1-2
        @Test
        public void 비밀번호_길이는_16자_초과일_수_없음() throws Exception {
            //given
            String wrongPassword = "qwer1234tyui5678a"; // 17글자

            //when

            //then
            throwIfWrongPasswordInput(wrongPassword)
                    .hasMessage(MemberExceptionMessage.Password.INVALID_PASSWORD_LENGTH.message());

        }

        // 1-2
        @Test
        public void 비밀번호는_영문_숫자_특수문자만_사용할_수_있음() throws Exception {
            //given
            String wrongPassword = "한글password123";

            //when

            //then
            throwIfWrongPasswordInput(wrongPassword)
                    .hasMessage(MemberExceptionMessage.Password.INVALID_PASSWORD_COMPOSITION.message());

        }

        // 2-1
        @Test
        public void 사용자_생년월일_YYYYMMDD가_비밀번호_포함_불가() throws Exception {
            //given
            LocalDate userBirthDate = LocalDate.of(2001, 2, 9);
            String wrongPassword = "pwd20010209!";

            //when

            //then
            throwIfPasswordContainsBirthDate(wrongPassword, userBirthDate)
                    .hasMessage(MemberExceptionMessage.Password.PASSWORD_CONTAINS_BIRTHDATE.message());
        }

        // 2-2
        @Test
        public void 사용자_생년월일_YYMMDD가_비밀번호_포함_불가() throws Exception {
            //given
            LocalDate userBirthDate = LocalDate.of(2001, 2, 9);
            String wrongPassword = "pass010209!";

            //when

            //then
            throwIfPasswordContainsBirthDate(wrongPassword, userBirthDate)
                    .hasMessage(MemberExceptionMessage.Password.PASSWORD_CONTAINS_BIRTHDATE.message());
        }

        // 4
        @Test
        public void 비밀번호는_암호화해_저장() throws Exception {
            //given

            //when
            String encodedPassword = PasswordEncryptor.encode(VALID_PASSWORD);

            //then
            assertThat(PasswordEncryptor.matches(VALID_PASSWORD, encodedPassword)).isTrue();
        }

        private AbstractThrowableAssert<?, ? extends Throwable> throwIfWrongPasswordInput(String wrongPassword) {
            return assertThatThrownBy(() -> Member.register(VALID_LOGIN_ID, wrongPassword, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        private AbstractThrowableAssert<?, ? extends Throwable> throwIfPasswordContainsBirthDate(String wrongPassword, LocalDate birthDate) {
            return assertThatThrownBy(() -> Member.register(VALID_LOGIN_ID, wrongPassword, VALID_NAME, birthDate, VALID_EMAIL))
                    .isInstanceOf(IllegalArgumentException.class);
        }

    }

    @DisplayName("이름(Name) 유효성 검증")
    @Nested
    class NameValidation {

        @Test
        void 이름은_2자_미만일_수_없음() {
            String shortName = "홍";
            throwIfWrongNameInput(shortName)
                    .hasMessage(MemberExceptionMessage.Name.TOO_SHORT.message());
        }

        @Test
        void 이름은_40자를_초과할_수_없음() {
            String longName = "가".repeat(41);
            throwIfWrongNameInput(longName)
                    .hasMessage(MemberExceptionMessage.Name.TOO_LONG.message());
        }

        @Test
        void 이름에_숫자가_포함될_수_없음() {
            String nameWithDigit = "홍길동1";
            throwIfWrongNameInput(nameWithDigit)
                    .hasMessage(MemberExceptionMessage.Name.CONTAINS_INVALID_CHAR.message());
        }

        @Test
        void 이름에_특수문자가_포함될_수_없음() {
            String nameWithSpecial = "John@";
            throwIfWrongNameInput(nameWithSpecial)
                    .hasMessage(MemberExceptionMessage.Name.CONTAINS_INVALID_CHAR.message());
        }

        private AbstractThrowableAssert<?, ? extends Throwable> throwIfWrongNameInput(String name) {
            return assertThatThrownBy(() -> Member.register(VALID_LOGIN_ID, VALID_PASSWORD, name, VALID_BIRTH_DATE, VALID_EMAIL))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @DisplayName("이메일(Email) 유효성 검증")
    @Nested
    class EmailValidation {

        @Test
        void 이메일_기본_형식을_준수해야_함() {
            String wrongEmail = "test#example.com"; // @ 없음
            throwIfWrongEmailInput(wrongEmail)
                    .hasMessage(MemberExceptionMessage.Email.INVALID_FORMAT.message());
        }

        @Test
        @DisplayName("이메일은 255자를 초과할 수 없음")
        void emailTooLong() {
            String longEmail = "a".repeat(250) + "@test.com";
            throwIfWrongEmailInput(longEmail)
                    .hasMessage(MemberExceptionMessage.Email.TOO_LONG.message());
        }

        private AbstractThrowableAssert<?, ? extends Throwable> throwIfWrongEmailInput(String email) {
            return assertThatThrownBy(() -> Member.register(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, email))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @DisplayName("생년월일(BirthDate) 유효성 검증")
    @Nested
    class BirthDateValidation {

        @Test
        @DisplayName("미래_날짜는_생년월일_등록_불가")
        void birthDateCannotBeFuture() {
            LocalDate futureDate = LocalDate.now().plusDays(1);
            throwIfWrongBirthDateInput(futureDate)
                    .hasMessage(MemberExceptionMessage.BirthDate.CANNOT_BE_FUTURE.message());
        }

        private AbstractThrowableAssert<?, ? extends Throwable> throwIfWrongBirthDateInput(LocalDate birthDate) {
            return assertThatThrownBy(() -> Member.register(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, birthDate, VALID_EMAIL))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @DisplayName("회원가입 통합 성공 검증")
    @Nested
    class RegistrationSuccess {

        @Test
        @DisplayName("모든 조건이 유효하면 회원가입에 성공한다")
        void successWhenAllFieldsValid() {
            assertThatCode(() -> Member.register(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL))
                    .doesNotThrowAnyException();
        }
    }

    @DisplayName("요청 시 비밀번호 동일한지 검증")
    @Nested
    class SamePasswordValidation {

        @Test
        @DisplayName("입력받은 비밀번호가 저장된 비밀번호와 정확히 일치하면 true를 반환한다")
        void isSamePassword_Success() {
            // given
            String savedPassword = "password123!";
            Member member = Member.builder()
                    .password(PasswordEncryptor.encode(savedPassword))
                    .build();

            // when
            boolean result = member.isSamePassword("password123!");

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("입력받은 비밀번호가 저장된 비밀번호와 다르면 false를 반환한다")
        void isSamePassword_Fail() {
            // given
            String savedPassword = "password123!";
            Member member = Member.builder()
                    .password(savedPassword)
                    .build();

            // when
            boolean result = member.isSamePassword("wrongPassword");

            // then
            assertThat(result).isFalse();
        }
    }

    @DisplayName("비밀번호 수정 정책 검증")
    @Nested
    class UpdatePasswordPolicy {

        @Test
        @DisplayName("새 비밀번호가 현재 비밀번호와 같으면 예외가 발생한다")
        void updatePassword_Fail_SameAsCurrent() {
            // given
            Member member = Member.builder()
                    .password(PasswordEncryptor.encode("oldPassword123!"))
                    .build();

            // when & then
            assertThatThrownBy(() -> member.updatePassword("oldPassword123!"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(MemberExceptionMessage.Password.PASSWORD_CANNOT_BE_SAME_AS_CURRENT.message());
        }

        @Test
        @DisplayName("새 비밀번호에 생년월일이 포함되면 예외가 발생한다")
        void updatePassword_Fail_ContainsBirthDate() {
            // given
            Member member = Member.builder()
                    .password("oldPass123!")
                    .birthDate(LocalDate.of(2001, 2, 9))
                    .build();

            // when & then
            assertThatThrownBy(() -> member.updatePassword("pass20010209!"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(MemberExceptionMessage.Password.PASSWORD_CONTAINS_BIRTHDATE.message());
        }

        @DisplayName("비밀번호 수정 시 형식 검증")
        @Nested
        class PasswordFormatValidation {

            // 1-1-1
            @Test
            public void 비밀번호_길이는_8자_미만일_수_없음() throws Exception {
                //given
                String wrongPassword = "pap1234"; // 7글자

                //when

                //then
                throwIfWrongPasswordInput(wrongPassword)
                        .hasMessage(MemberExceptionMessage.Password.INVALID_PASSWORD_LENGTH.message());
            }

            // 1-1-2
            @Test
            public void 비밀번호_길이는_16자_초과일_수_없음() throws Exception {
                //given
                String wrongPassword = "qwer1234tyui5678a"; // 17글자

                //when

                //then
                throwIfWrongPasswordInput(wrongPassword)
                        .hasMessage(MemberExceptionMessage.Password.INVALID_PASSWORD_LENGTH.message());

            }

            // 1-2
            @Test
            public void 비밀번호는_영문_숫자_특수문자만_사용할_수_있음() throws Exception {
                //given
                String wrongPassword = "한글password123";

                //when

                //then
                throwIfWrongPasswordInput(wrongPassword)
                        .hasMessage(MemberExceptionMessage.Password.INVALID_PASSWORD_COMPOSITION.message());

            }

            // 2-1
            @Test
            public void 사용자_생년월일_YYYYMMDD가_비밀번호_포함_불가() throws Exception {
                //given
                LocalDate userBirthDate = LocalDate.of(2001, 2, 9);
                String wrongPassword = "pwd20010209!";

                //when

                //then
                throwIfWrongPasswordInput(wrongPassword)
                        .hasMessage(MemberExceptionMessage.Password.PASSWORD_CONTAINS_BIRTHDATE.message());
            }

            // 2-2
            @Test
            public void 사용자_생년월일_YYMMDD가_비밀번호_포함_불가() throws Exception {
                //given
                LocalDate userBirthDate = LocalDate.of(2001, 2, 9);
                String wrongPassword = "pass010209!";

                //when

                //then
                throwIfWrongPasswordInput(wrongPassword)
                        .hasMessage(MemberExceptionMessage.Password.PASSWORD_CONTAINS_BIRTHDATE.message());
            }

            // 4
            @Test
            public void 비밀번호는_암호화해_저장() throws Exception {
                //given

                //when
                String encodedPassword = PasswordEncryptor.encode(VALID_PASSWORD);

                //then
                assertThat(PasswordEncryptor.matches(VALID_PASSWORD, encodedPassword)).isTrue();
            }

            private AbstractThrowableAssert<?, ? extends Throwable> throwIfWrongPasswordInput(String wrongPassword) {
                Member member = Member.builder()
                        .password("oldPass123!")
                        .birthDate(LocalDate.of(2001, 2, 9))
                        .build();
                return assertThatThrownBy(() -> member.updatePassword(wrongPassword))
                        .isInstanceOf(IllegalArgumentException.class);
            }

        }
    }
}
