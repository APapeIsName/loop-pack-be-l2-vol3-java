package com.loopers.domain.member;

import lombok.AllArgsConstructor;

public class MemberExceptionMessage {

    public interface ExceptionMessage {
        String message();
    }

    /**
     * 1_000 ~ 1_099: 로그인 ID 관련 오류
     */
    @AllArgsConstructor
    public enum LoginId implements ExceptionMessage {
        INVALID_ID_FORMAT("아이디는 영문이 포함되어야 하며, 한글이나 특수문자는 사용할 수 없습니다.", 1_001),
        INVALID_ID_NUMERIC_ONLY("아이디를 숫자만으로 구성할 수 없습니다. 영문을 포함해주세요.", 1_002),
        DUPLICATE_ID_EXISTS("이미 사용 중인 아이디입니다.", 1_003),
        INVALID_ID_LENGTH("아이디는 6글자 이상, 20글자 이하여야 합니다.", 1_004)
        ;

        private final String message;
        private final Integer code;

        public String message() {
            return message;
        }
    }

    /**
     * 1_100 ~ 1_199: 비밀번호 관련 오류
     */

    @AllArgsConstructor
    public enum Password implements ExceptionMessage {
        // 1. 기본 형식 검증 (Basic Format)
        // 1-1. 길이 제한 (8~16자)
        INVALID_PASSWORD_LENGTH("비밀번호는 8자 이상 16자 이하여야 합니다.", 1_101),

        // 메시지 수정: '모두 포함' -> '영문, 숫자, 특수문자만 사용 가능'
        INVALID_PASSWORD_COMPOSITION("비밀번호는 영문, 숫자, 특수문자만 사용할 수 있으며 한글 등은 포함할 수 없습니다.", 1_102),

        // 2. 생년월일 포함 금지 규칙 (Zero-Birthdate Policy)
        // 2-1, 2-2, 2-3 통합 검증
        PASSWORD_CONTAINS_BIRTHDATE("비밀번호에 생년월일 정보(YYYYMMDD 또는 YYMMDD)를 포함할 수 없습니다.", 1_103),

        // 3. 수정 시 정책 (Update Policy)
        // 3-1. 재사용 금지
        PASSWORD_CANNOT_BE_SAME_AS_CURRENT("현재 사용 중인 비밀번호와 동일한 비밀번호로 변경할 수 없습니다.", 1_104),

        PASSWORD_NOT_ENCODED("비밀번호가 암호화되지 않았습니다.", 1_105),

        PASSWORD_INCORRECT("비밀번호가 일치하지 않습니다.", 1_106)

        ;

        private final String message;
        private final Integer code;

        public String message() {
            return message;
        }
    }

    /**
     * 1_200 ~ 1_219: 이름(Name) 관련 오류
     */
    @AllArgsConstructor
    public enum Name implements ExceptionMessage {
        TOO_SHORT("이름은 최소 2자 이상이어야 합니다.", 1_201),
        TOO_LONG("이름은 최대 40자까지 가능합니다.", 1_202),
        CONTAINS_INVALID_CHAR("이름에 숫자나 특수문자를 포함할 수 없습니다. 한글과 영문만 가능합니다.", 1_203);

        private final String message;
        private final Integer code;
        public String message() { return message; }
    }

    /**
     * 1_220 ~ 1_239: 이메일(Email) 관련 오류
     */
    @AllArgsConstructor
    public enum Email implements ExceptionMessage {
        INVALID_FORMAT("유효하지 않은 이메일 형식입니다.", 1_221),
        TOO_LONG("이메일은 255자를 초과할 수 없습니다.", 1_222);

        private final String message;
        private final Integer code;
        public String message() { return message; }
    }

    /**
     * 1_240 ~ 1_259: 생년월일(BirthDate) 관련 오류
     */
    @AllArgsConstructor
    public enum BirthDate implements ExceptionMessage {
        CANNOT_BE_FUTURE("생년월일은 미래 날짜일 수 없습니다.", 1_241);

        private final String message;
        private final Integer code;
        public String message() { return message; }
    }

    /**
     * 1_300 ~ 1_399: 회원(Member) 존재 여부 관련 오류
     */
    @AllArgsConstructor
    public enum ExistsMember implements ExceptionMessage {
        CANNOT_LOGIN("아이디나 비밀번호가 잘못됐습니다.", 1_301);

        private final String message;
        private final Integer code;

        public String message() {
            return message;
        }
    }

}
