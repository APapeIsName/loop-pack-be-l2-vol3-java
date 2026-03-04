package com.loopers.application;

import com.loopers.application.service.MemberService;
import com.loopers.application.service.dto.MemberRegisterCommand;
import com.loopers.application.service.dto.MemberInfo;
import com.loopers.domain.member.MemberExceptionMessage;
import com.loopers.domain.member.MemberRepository;
import com.loopers.domain.member.PasswordEncryptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class MemberServiceIntegrationTest {

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncryptor passwordEncryptor;

    private static final LocalDate BIRTH_DATE = LocalDate.of(2001, 2, 9);

    private void 회원을_등록한다(String loginId, String password) {
        memberService.register(new MemberRegisterCommand(
                loginId, password, "공명선", BIRTH_DATE, "test@loopers.com"));
    }

    @Test
    void 회원가입_성공() {
        // given
        String inputId = "integrationId123";
        MemberRegisterCommand request = new MemberRegisterCommand(
                inputId, "Pass!1234", "공명선", BIRTH_DATE, "test@loopers.com");

        // when
        memberService.register(request);

        // then
        assertThat(memberRepository.existsByLoginId(inputId)).isTrue();
    }

    @Test
    void 회원가입_시_중복_아이디_사용_불가() {
        // given
        String duplicateId = "existingId";
        회원을_등록한다(duplicateId, "encodedPw1!");

        MemberRegisterCommand request = new MemberRegisterCommand(
                duplicateId, "NewPass!123", "신규유저", LocalDate.of(2000, 1, 1), "new@test.com");

        // when & then
        assertThatThrownBy(() -> memberService.register(request))
                .hasMessage(MemberExceptionMessage.LoginId.DUPLICATE_ID_EXISTS.message());
    }

    @Test
    void 내_정보_조회_성공_loginId_반환() {
        // given
        String loginId = "tester123";
        String password = "password123!";
        회원을_등록한다(loginId, password);

        // when
        MemberInfo response = memberService.getMyInfo(loginId, password);

        // then
        assertThat(response.loginId()).isEqualTo(loginId);
    }

    @Test
    void 내_정보_조회_성공_이름_반환() {
        // given
        String loginId = "tester123";
        String password = "password123!";
        회원을_등록한다(loginId, password);

        // when
        MemberInfo response = memberService.getMyInfo(loginId, password);

        // then
        assertThat(response.name()).isEqualTo("공명선");
    }

    @Test
    void 내_정보_조회_성공_이메일_반환() {
        // given
        String loginId = "tester123";
        String password = "password123!";
        회원을_등록한다(loginId, password);

        // when
        MemberInfo response = memberService.getMyInfo(loginId, password);

        // then
        assertThat(response.email()).isEqualTo("test@loopers.com");
    }

    @Test
    void 내_정보_조회_실패_비밀번호_불일치() {
        // given
        String loginId = "tester123";
        회원을_등록한다(loginId, "password123!");

        // when & then
        assertThatThrownBy(() -> memberService.getMyInfo(loginId, "wrongPassword1!"))
                .hasMessage(MemberExceptionMessage.ExistsMember.CANNOT_LOGIN.message());
    }

    @Test
    void 내_정보_조회_실패_존재하지_않는_아이디() {
        // given
        String unknownId = "nobody12";

        // when & then
        assertThatThrownBy(() -> memberService.getMyInfo(unknownId, "anyPassword1!"))
                .hasMessage(MemberExceptionMessage.ExistsMember.CANNOT_LOGIN.message());
    }

    @Test
    void 비밀번호_수정_성공() {
        // given
        String loginId = "tester123";
        String currentPw = "oldPass123!";
        String newPw = "newPass5678@";
        회원을_등록한다(loginId, currentPw);

        // when
        memberService.updatePassword(loginId, currentPw, newPw);

        // then
        assertThat(memberRepository.findByLoginId(loginId).orElseThrow()
                .matchesPassword(newPw, passwordEncryptor)
        ).isTrue();
    }

    @Test
    void 비밀번호_수정_실패_현재_비밀번호와_동일() {
        // given
        String loginId = "tester123";
        String currentPw = "oldPass123!";
        회원을_등록한다(loginId, currentPw);

        // when & then
        assertThatThrownBy(() -> memberService.updatePassword(loginId, currentPw, currentPw))
                .hasMessage(MemberExceptionMessage.Password.PASSWORD_CANNOT_BE_SAME_AS_CURRENT.message());
    }

    @Test
    void 비밀번호_수정_실패_생년월일_포함() {
        // given
        String loginId = "tester123";
        String currentPw = "oldPass123!";
        회원을_등록한다(loginId, currentPw);

        // when & then
        assertThatThrownBy(() -> memberService.updatePassword(loginId, currentPw, "pass20010209!"))
                .hasMessage(MemberExceptionMessage.Password.PASSWORD_CONTAINS_BIRTHDATE.message());
    }

    @Test
    void 비밀번호_수정_실패_현재_비밀번호_불일치() {
        // given
        String loginId = "tester123";
        회원을_등록한다(loginId, "correct123!");

        // when & then
        assertThatThrownBy(() -> memberService.updatePassword(loginId, "wrong123!!", "newPass123!"))
                .hasMessage(MemberExceptionMessage.Password.PASSWORD_INCORRECT.message());
    }
}
