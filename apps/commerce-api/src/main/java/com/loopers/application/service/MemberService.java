package com.loopers.application.service;

import com.loopers.application.service.dto.MemberRegisterRequest;
import com.loopers.application.service.dto.MyMemberInfoResponse;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberExceptionMessage;
import com.loopers.infrastructure.member.MemberRepository;
import com.loopers.support.error.CoreException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;

    @Transactional
    public void register(MemberRegisterRequest request) {
        boolean isLoginIdAlreadyExists = memberRepository.existsByLoginId(request.loginId());

        if (isLoginIdAlreadyExists) {
            throw new IllegalArgumentException(MemberExceptionMessage.LoginId.DUPLICATE_ID_EXISTS.message());
        }

        memberRepository.save(Member.register(request.loginId(), request.password(), request.name(), request.birthdate(), request.email()));
    }

    @Transactional(readOnly = true)
    public MyMemberInfoResponse getMyInfo(String userId, String password) {
        // 1. 회원 조회 (없으면 예외 발생 - MemberExceptionMessage.Common.NOT_FOUND 사용)
        Member member = memberRepository.findByLoginId(userId)
                .orElseThrow(() -> new IllegalArgumentException(MemberExceptionMessage.ExistsMember.CANNOT_LOGIN.message()));

        // 2. 비밀번호 일치 여부 확인 (도메인 모델의 isSamePassword 활용)
        if (!member.isSamePassword(password)) {
            // 비밀번호 불일치 시 예외 발생 (인증 관련 메시지 사용)
            throw new IllegalArgumentException(MemberExceptionMessage.ExistsMember.CANNOT_LOGIN.message());
        }

        // 3. DTO 변환 및 이름 마스킹 처리
        return new MyMemberInfoResponse(
                member.getLoginId(),
                maskName(member.getName()), // 마스킹 로직 적용
                member.getBirthDate(),
                member.getEmail()
        );
    }

    @Transactional
    public void updatePassword(String userId, String currentPassword, String newPassword) {
        // 1. 회원 조회
        Member member = memberRepository.findByLoginId(userId)
                .orElseThrow(() -> new IllegalArgumentException(MemberExceptionMessage.ExistsMember.CANNOT_LOGIN.message()));

        // 2. 본인 확인 (기존 비밀번호 일치 여부)
        if (!member.isSamePassword(currentPassword)) {
            throw new IllegalArgumentException(MemberExceptionMessage.Password.PASSWORD_INCORRECT.message()); // 적절한 메시지로 변경 가능
        }

        // 4. 도메인 정책 검증 및 수정 (생년월일 포함 여부 등은 도메인 내 로직에서 처리)
        member.updatePassword(newPassword);
    }

    /**
     * 이름의 마지막 글자를 *로 마스킹 처리
     */
    private String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        if (name.length() == 1) {
            return "*";
        }
        return name.substring(0, name.length() - 1) + "*";
    }
}
