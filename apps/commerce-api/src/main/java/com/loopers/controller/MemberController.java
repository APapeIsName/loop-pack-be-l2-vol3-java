package com.loopers.controller;

import com.loopers.application.service.MemberService;
import com.loopers.application.service.dto.MemberRegisterRequest;
import com.loopers.application.service.dto.MyMemberInfoResponse;
import com.loopers.application.service.dto.PasswordUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    /**
     * 회원가입
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@RequestBody MemberRegisterRequest request) {
        memberService.register(request);
    }

    /**
     * 내 정보 조회
     * 헤더 인증 (ID, PW) 기반
     */
    @GetMapping("/me")
    public MyMemberInfoResponse getMyInfo(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String password
    ) {
        return memberService.getMyInfo(loginId, password);
    }

    /**
     * 비밀번호 수정
     * 헤더 인증 (ID, 기존 PW) + 바디 (새 PW)
     */
    @PatchMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updatePassword(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String currentPassword,
            @RequestBody PasswordUpdateRequest request
    ) {
        memberService.updatePassword(loginId, currentPassword, request.newPassword());
    }
}
