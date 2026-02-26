package com.loopers.domain.member;

import com.loopers.domain.BaseTimeEntity;
import com.loopers.domain.member.vo.Email;
import com.loopers.domain.member.vo.LoginId;
import com.loopers.domain.member.vo.MemberId;
import com.loopers.domain.member.vo.MemberName;
import com.loopers.domain.member.vo.Password;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Objects;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "member")
public class Member extends BaseTimeEntity {

    @Embedded
    private LoginId loginId;

    @Embedded
    private Password password;

    @Embedded
    private MemberName name;

    private LocalDate birthDate;

    @Embedded
    private Email email;

    private Member(LoginId loginId, Password password, MemberName name, LocalDate birthDate, Email email) {
        validateBirthDate(birthDate);
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
    }

    public static Member register(
            LoginId loginId,
            Password password,
            MemberName name,
            LocalDate birthDate,
            Email email
    ) {
        return new Member(loginId, password, name, birthDate, email);
    }

    public MemberId getMemberId() {
        return getId() != null ? MemberId.of(getId()) : null;
    }

    public void updatePassword(String newRawPassword, PasswordEncryptor encryptor) {
        this.password = password.changeTo(newRawPassword, birthDate, encryptor);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Member member)) return false;
        return getId() != null && Objects.equals(getId(), member.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    private static void validateBirthDate(LocalDate birthDate) {
        if (birthDate == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 필수입니다.");
        }
        if (birthDate.isAfter(LocalDate.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST, MemberExceptionMessage.BirthDate.CANNOT_BE_FUTURE.message());
        }
    }
}
