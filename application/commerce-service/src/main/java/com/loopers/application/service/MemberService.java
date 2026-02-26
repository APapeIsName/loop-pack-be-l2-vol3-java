package com.loopers.application.service;

import com.loopers.application.service.dto.RegisterMemberCommand;
import com.loopers.application.service.dto.MemberInfo;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberExceptionMessage;
import com.loopers.domain.member.MemberRepository;
import com.loopers.domain.member.PasswordEncryptor;
import com.loopers.domain.member.vo.*;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncryptor passwordEncryptor;

    @Transactional
    public void register(RegisterMemberCommand request) {
        boolean isLoginIdAlreadyExists = memberRepository.existsByLoginId(request.loginId());

        if (isLoginIdAlreadyExists) {
            throw new CoreException(ErrorType.CONFLICT, MemberExceptionMessage.LoginId.DUPLICATE_ID_EXISTS.message());
        }

        Member member = Member.register(
                LoginId.of(request.loginId()),
                Password.of(request.password(), request.birthdate(), passwordEncryptor),
                MemberName.of(request.name()),
                request.birthdate(),
                Email.of(request.email())
        );
        memberRepository.save(member);
    }

    @Transactional(readOnly = true)
    public MemberInfo getMyInfo(String userId, String password) {
        Member member = memberRepository.findByLoginId(userId)
                .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, MemberExceptionMessage.ExistsMember.CANNOT_LOGIN.message()));

        if (!member.getPassword().matches(password, passwordEncryptor)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, MemberExceptionMessage.ExistsMember.CANNOT_LOGIN.message());
        }

        return new MemberInfo(
                member.getLoginId(),
                member.getName(),
                member.getBirthDate(),
                member.getEmail()
        );
    }

    @Transactional
    public void updatePassword(String userId, String currentPassword, String newPassword) {
        Member member = memberRepository.findByLoginId(userId)
                .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, MemberExceptionMessage.ExistsMember.CANNOT_LOGIN.message()));

        if (!member.getPassword().matches(currentPassword, passwordEncryptor)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, MemberExceptionMessage.Password.PASSWORD_INCORRECT.message());
        }

        member.updatePassword(newPassword, passwordEncryptor);
    }
}
