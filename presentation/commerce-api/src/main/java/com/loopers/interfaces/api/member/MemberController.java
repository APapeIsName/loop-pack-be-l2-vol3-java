package com.loopers.interfaces.api.member;

import com.loopers.application.service.MemberService;
import com.loopers.application.service.dto.MemberRegisterCommand;
import com.loopers.application.service.dto.PasswordUpdateCommand;
import com.loopers.interfaces.api.member.dto.MemberApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@RequestBody MemberRegisterCommand request) {
        memberService.register(request);
    }

    @GetMapping("/me")
    public MemberApiResponse getMyInfo(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String password
    ) {
        return MemberApiResponse.from(memberService.getMyInfo(loginId, password));
    }

    @PatchMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updatePassword(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String currentPassword,
            @RequestBody PasswordUpdateCommand request
    ) {
        memberService.updatePassword(loginId, currentPassword, request.newPassword());
    }
}
