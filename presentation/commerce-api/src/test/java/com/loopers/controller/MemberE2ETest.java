package com.loopers.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.service.dto.MemberRegisterCommand;
import com.loopers.application.service.dto.PasswordUpdateCommand;
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

    private static final String LOGIN_ID = "loopers123";
    private static final String INITIAL_PW = "Initial!1234";
    private static final String NEW_PW = "Updated!5678";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("회원가입 성공 시 201 Created를 반환한다")
    void 회원가입_성공() throws Exception {
        // given
        MemberRegisterCommand request = createRegisterRequest();

        // when & then
        mockMvc.perform(post("/api/members/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("내 정보 조회 시 마스킹된 이름이 반환된다")
    void 내_정보_조회_마스킹된_이름_반환() throws Exception {
        // given
        registerMember();

        // when & then
        mockMvc.perform(get("/api/members/me")
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", INITIAL_PW))
                .andExpect(jsonPath("$.name").value("공명*"));
    }

    @Test
    @DisplayName("비밀번호 변경 성공 시 204 No Content를 반환한다")
    void 비밀번호_변경_성공() throws Exception {
        // given
        registerMember();

        // when & then
        mockMvc.perform(patch("/api/members/password")
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", INITIAL_PW)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordUpdateCommand(NEW_PW))))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("변경된 비밀번호로 내 정보 조회가 성공한다")
    void 변경된_비밀번호로_조회_성공() throws Exception {
        // given
        registerMember();
        changePassword();

        // when & then
        mockMvc.perform(get("/api/members/me")
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", NEW_PW))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("비밀번호 변경 후 기존 비밀번호로 조회하면 401 Unauthorized를 반환한다")
    void 기존_비밀번호로_조회_실패() throws Exception {
        // given
        registerMember();
        changePassword();

        // when & then
        mockMvc.perform(get("/api/members/me")
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", INITIAL_PW))
                .andExpect(status().isUnauthorized());
    }

    private MemberRegisterCommand createRegisterRequest() {
        return new MemberRegisterCommand(
                LOGIN_ID, INITIAL_PW, "공명선", LocalDate.of(2001, 2, 9), "test@loopers.com"
        );
    }

    private void registerMember() throws Exception {
        mockMvc.perform(post("/api/members/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRegisterRequest())));
    }

    private void changePassword() throws Exception {
        mockMvc.perform(patch("/api/members/password")
                .header("X-Loopers-LoginId", LOGIN_ID)
                .header("X-Loopers-LoginPw", INITIAL_PW)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PasswordUpdateCommand(NEW_PW))));
    }
}
