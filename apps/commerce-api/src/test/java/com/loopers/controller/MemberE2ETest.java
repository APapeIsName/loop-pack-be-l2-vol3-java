package com.loopers.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.service.dto.MemberRegisterRequest;
import com.loopers.application.service.dto.PasswordUpdateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class MemberE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("전체 시나리오: 회원가입 - 내 정보 조회 - 비밀번호 변경")
    void member_full_lifecycle_scenario() throws Exception {
        // [1] 회원가입 (Register)
        String loginId = "loopers123";
        String initialPw = "Initial!1234";
        MemberRegisterRequest registerRequest = new MemberRegisterRequest(
                loginId, initialPw, "공명선", LocalDate.of(2001, 2, 9), "test@loopers.com"
        );

        mockMvc.perform(post("/api/members/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        // [2] 내 정보 조회 (Get Info) - 마스킹 확인
        mockMvc.perform(get("/api/members/me")
                        .header("X-Loopers-LoginId", loginId)
                        .header("X-Loopers-LoginPw", initialPw))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value(loginId))
                .andExpect(jsonPath("$.name").value("공명*")) // 마지막 글자 마스킹
                .andExpect(jsonPath("$.email").value("test@loopers.com"));

        // [3] 비밀번호 수정 (Update Password)
        String newPw = "Updated!5678";
        PasswordUpdateRequest updateRequest = new PasswordUpdateRequest(newPw);

        mockMvc.perform(patch("/api/members/password")
                        .header("X-Loopers-LoginId", loginId)
                        .header("X-Loopers-LoginPw", initialPw) // 수정 요청도 기존 헤더 인증 필요
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNoContent());

        // [4] 변경된 비밀번호로 내 정보 재조회 (Re-verify)
        // 새 비밀번호로 헤더를 보내야만 성공해야 함
        mockMvc.perform(get("/api/members/me")
                        .header("X-Loopers-LoginId", loginId)
                        .header("X-Loopers-LoginPw", newPw)) // New Password
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value(loginId));

        // [5] 기존 비밀번호로 조회 시도 (Should Fail)
        mockMvc.perform(get("/api/members/me")
                        .header("X-Loopers-LoginId", loginId)
                        .header("X-Loopers-LoginPw", initialPw)) // Old Password
                .andExpect(status().isUnauthorized()); // 또는 400 에러 (설정한 예외 처리에 따름)
    }

}
