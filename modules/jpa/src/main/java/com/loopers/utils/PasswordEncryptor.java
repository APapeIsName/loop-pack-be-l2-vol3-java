package com.loopers.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class PasswordEncryptor {

    private static final String ALGORITHM = "SHA-256";

    /**
     * 비밀번호를 SHA-256으로 해싱함.
     * (주의: 이 예제는 순수 해싱만 수행합니다. 실무에선 Salt를 추가해야 안전합니다.)
     */
    public static String encode(String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] encodedHash = digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8));

            // 바이트 배열을 읽기 쉬운 문자열(Base64)로 변환
            return Base64.getEncoder().encodeToString(encodedHash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("암호화 알고리즘을 찾을 수 없습니다.", e);
        }
    }

    /**
     * 일치 여부 확인
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) return false;

        // 입력받은 원문을 똑같이 해싱해서 결과가 같은지 비교
        String newHash = encode(rawPassword);
        return newHash.equals(encodedPassword);
    }
}
