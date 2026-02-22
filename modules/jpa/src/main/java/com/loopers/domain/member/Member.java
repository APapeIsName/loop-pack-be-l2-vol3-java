package com.loopers.domain.member;

import com.loopers.domain.member.policy.*;
import com.loopers.utils.PasswordEncryptor;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "member")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String loginId;

    private String password;

    private String name;

    private LocalDate birthDate;

    private String email;

    public static Member register(
            String loginId,
            String password,
            String name,
            LocalDate birthDate,
            String email
    ) {
        MemberPolicy.LoginId.validate(loginId);
        MemberPolicy.BirthDate.validate(birthDate);
        MemberPolicy.Password.validate(password, birthDate);
        MemberPolicy.Name.validate(name);
        MemberPolicy.Email.validate(email);

        return Member.builder()
                .loginId(loginId)
                .password(encodedPassword(password))
                .name(name)
                .birthDate(birthDate)
                .email(email)
                .build();
    }

    public boolean isSamePassword(String inputPassword) {
        return PasswordEncryptor.matches(inputPassword, this.password);
    }

    public void updatePassword(String newPassword) {
        if (isSamePassword(newPassword)) {
            throw new IllegalArgumentException(MemberExceptionMessage.Password.PASSWORD_CANNOT_BE_SAME_AS_CURRENT.message());
        }
        MemberPolicy.Password.validate(newPassword, birthDate);

        this.password = encodedPassword(newPassword);
    }

    private static String encodedPassword(String password) {
        return PasswordEncryptor.encode(password);
    }

}
