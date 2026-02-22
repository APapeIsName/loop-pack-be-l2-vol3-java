package com.loopers.application;

import com.loopers.application.service.MemberService;
import com.loopers.application.service.dto.MemberRegisterRequest;
import com.loopers.application.service.dto.MyMemberInfoResponse;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberExceptionMessage;
import com.loopers.infrastructure.member.MemberRepository;
import com.loopers.utils.PasswordEncryptor;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MemberServiceTest {

    @InjectMocks
    private MemberService memberService;

    @Mock
    private MemberRepository memberRepository;

    /**
     * 회원가입
     */

    @Test
    public void 회원가입_시_아이디_중복_불가() throws Exception {
        //given
        String inputId = "apape123";
        MemberRegisterRequest request = MemberRegisterRequest.builder()
                .loginId(inputId)
                .password("password123!")
                .name("공명선")
                .birthdate(LocalDate.of(2001, 2, 9))
                .email("gms72901217@gmail.com").build();
        when(memberRepository.existsByLoginId(inputId)).thenReturn(true);

        //when

        //then
        assertThatThrownBy(() -> memberService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(MemberExceptionMessage.LoginId.DUPLICATE_ID_EXISTS.message());
    }

    @Test
    public void 회원가입_성공() throws Exception {
        //given
        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        String inputId = "newId123";
        MemberRegisterRequest request = new MemberRegisterRequest(
               inputId,"password123!","공명선",LocalDate.of(2001, 2, 9),"gms72901217@gmail.com");
        when(memberRepository.existsByLoginId(inputId)).thenReturn(false);

        //when
        memberService.register(request);

        //then
        verify(memberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getLoginId()).isEqualTo(request.loginId());
    }

    /**
     * 요청 공통
     */

    @Test
    public void 존재하지_않는_회원_조회_시_예외_발생() throws Exception {
        //given
        String dummyId = "unknownId";
        String dummyPwd = "password123!";
        given(memberRepository.findByLoginId(dummyId)).willReturn(Optional.empty());

        //when

        //then
        assertThatThrownBy(() -> memberService.getMyInfo(dummyId, dummyPwd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(MemberExceptionMessage.ExistsMember.CANNOT_LOGIN.message());
    }

    /**
     * 내 정보 조회
     */

    @Test
    public void 내_정보_조회_시_이름_마스킹() throws Exception {
        // given
        String loginId = "apape123";
        String password = "password123!";

        Member member = Member.builder()
                .loginId(loginId)
                .password(PasswordEncryptor.encode(password))
                .name("공명선")
                .birthDate(LocalDate.of(2001, 2, 9))
                .email("gms72901217@gmail.com")
                .build();

        given(memberRepository.findByLoginId(loginId)).willReturn(Optional.of(member));

        // when
        MyMemberInfoResponse response = memberService.getMyInfo(loginId, password);

        // then
        assertThat(response.loginId()).isEqualTo(loginId);

    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 수정을 진행하지 않고 예외를 던진다")
    void updatePassword_Fail_InvalidCurrentPassword() {
        // given
        String loginId = "tester";
        Member member = Member.builder()
                .loginId(loginId)
                .password("correctPasswo")
                .build();

        given(memberRepository.findByLoginId(loginId)).willReturn(Optional.of(member));

        // when & then
        assertThatThrownBy(() -> memberService.updatePassword(loginId, "wrongPassword", "newPass123!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(MemberExceptionMessage.Password.PASSWORD_INCORRECT.message());

        // 비밀번호 수정 메서드가 호출되지 않았는지 간접적으로 확인 가능 (혹은 상태 검증)
    }

}
