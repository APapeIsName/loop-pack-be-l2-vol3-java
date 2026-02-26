package com.loopers.application;

import com.loopers.application.service.MemberService;
import com.loopers.application.service.dto.RegisterMemberCommand;
import com.loopers.application.service.dto.MemberInfo;
import com.loopers.domain.member.*;
import com.loopers.domain.member.vo.LoginId;
import com.loopers.domain.member.vo.MemberName;
import com.loopers.domain.member.vo.Password;
import com.loopers.domain.member.PasswordEncryptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @InjectMocks
    private MemberService memberService;

    @Mock
    private MemberRepository memberRepository;

    @Spy
    private PasswordEncryptor passwordEncryptor = new FakePasswordEncryptor();

    @Test
    void 회원가입_시_아이디_중복_불가() {
        // given
        String inputId = "apape123";
        RegisterMemberCommand request = new RegisterMemberCommand(
                inputId, "password123!", "공명선", LocalDate.of(2001, 2, 9), "gms72901217@gmail.com");
        when(memberRepository.existsByLoginId(inputId)).thenReturn(true);

        // when

        // then
        assertThatThrownBy(() -> memberService.register(request))
                .hasMessage(MemberExceptionMessage.LoginId.DUPLICATE_ID_EXISTS.message());
    }

    @Test
    void 회원가입_성공_시_저장된다() {
        // given
        String inputId = "newId123";
        RegisterMemberCommand request = new RegisterMemberCommand(
               inputId, "password123!", "공명선", LocalDate.of(2001, 2, 9), "gms72901217@gmail.com");
        when(memberRepository.existsByLoginId(inputId)).thenReturn(false);

        // when
        memberService.register(request);

        // then
        verify(memberRepository).save(any(Member.class));
    }

    @Test
    void 존재하지_않는_회원_조회_시_예외_발생() {
        // given
        String dummyId = "unknownId";
        String dummyPwd = "password123!";
        given(memberRepository.findByLoginId(dummyId)).willReturn(Optional.empty());

        // when

        // then
        assertThatThrownBy(() -> memberService.getMyInfo(dummyId, dummyPwd))
                .hasMessage(MemberExceptionMessage.ExistsMember.CANNOT_LOGIN.message());
    }

    @Test
    void 내_정보_조회_성공_loginId_반환() {
        // given
        String loginId = "apape123";
        String password = MemberFixture.DEFAULT_RAW_PASSWORD;
        Member member = MemberFixture.create(LoginId.of(loginId));
        given(memberRepository.findByLoginId(loginId)).willReturn(Optional.of(member));

        // when
        MemberInfo response = memberService.getMyInfo(loginId, password);

        // then
        assertThat(response.loginId()).isEqualTo(LoginId.of(loginId));
    }

    @Test
    void 내_정보_조회_성공_name_반환() {
        // given
        String loginId = "apape123";
        String password = MemberFixture.DEFAULT_RAW_PASSWORD;
        Member member = MemberFixture.create(LoginId.of(loginId));
        given(memberRepository.findByLoginId(loginId)).willReturn(Optional.of(member));

        // when
        MemberInfo response = memberService.getMyInfo(loginId, password);

        // then
        assertThat(response.name()).isEqualTo(MemberName.of("홍길동"));
    }

    @Test
    void 현재_비밀번호가_틀리면_수정_불가() {
        // given
        String loginId = "tester12";
        String correctPassword = "correctPw1!";
        Member member = MemberFixture.create(
                LoginId.of(loginId),
                Password.of(correctPassword, MemberFixture.DEFAULT_BIRTH_DATE, new FakePasswordEncryptor())
        );
        given(memberRepository.findByLoginId(loginId)).willReturn(Optional.of(member));

        // when & then
        assertThatThrownBy(() -> memberService.updatePassword(loginId, "wrongPassword1!", "newPass123!"))
                .hasMessage(MemberExceptionMessage.Password.PASSWORD_INCORRECT.message());
    }
}
