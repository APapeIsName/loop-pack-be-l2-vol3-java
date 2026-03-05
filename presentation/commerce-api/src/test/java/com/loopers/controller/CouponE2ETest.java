package com.loopers.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.service.dto.MemberRegisterCommand;
import com.loopers.domain.coupon.CouponType;
import com.loopers.interfaces.api.coupon.dto.CouponCreateApiRequest;
import com.loopers.interfaces.api.coupon.dto.CouponUpdateApiRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CouponE2ETest {

    private static final String LOGIN_ID = "coupontest123";
    private static final String PASSWORD = "Coupon!1234";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        회원을_등록한다();
    }

    @Test
    void 쿠폰_템플릿_생성_201() throws Exception {
        // when & then
        mockMvc.perform(post("/api-admin/v1/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CouponCreateApiRequest("3000원 할인", CouponType.FIXED, 3000, 10000L,
                                        ZonedDateTime.now().plusDays(30)))))
                .andExpect(status().isCreated());
    }

    @Test
    void 쿠폰_템플릿_목록_조회_200() throws Exception {
        // given
        쿠폰을_생성한다("3000원 할인", CouponType.FIXED, 3000);
        쿠폰을_생성한다("10% 할인", CouponType.RATE, 10);

        // when & then
        mockMvc.perform(get("/api-admin/v1/coupons"))
                .andExpect(status().isOk());
    }

    @Test
    void 쿠폰_템플릿_목록_조회_시_생성한_쿠폰_수_반환() throws Exception {
        // given
        쿠폰을_생성한다("3000원 할인", CouponType.FIXED, 3000);
        쿠폰을_생성한다("10% 할인", CouponType.RATE, 10);

        // when & then
        mockMvc.perform(get("/api-admin/v1/coupons"))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void 쿠폰_템플릿_상세_조회_200() throws Exception {
        // given
        Long couponId = 쿠폰을_생성하고_ID를_반환한다("3000원 할인", CouponType.FIXED, 3000);

        // when & then
        mockMvc.perform(get("/api-admin/v1/coupons/{couponId}", couponId))
                .andExpect(status().isOk());
    }

    @Test
    void 쿠폰_템플릿_상세_조회_시_이름_반환() throws Exception {
        // given
        Long couponId = 쿠폰을_생성하고_ID를_반환한다("3000원 할인", CouponType.FIXED, 3000);

        // when & then
        mockMvc.perform(get("/api-admin/v1/coupons/{couponId}", couponId))
                .andExpect(jsonPath("$.name").value("3000원 할인"));
    }

    @Test
    void 쿠폰_템플릿_수정_200() throws Exception {
        // given
        Long couponId = 쿠폰을_생성하고_ID를_반환한다("3000원 할인", CouponType.FIXED, 3000);

        // when & then
        mockMvc.perform(put("/api-admin/v1/coupons/{couponId}", couponId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CouponUpdateApiRequest("5000원 할인", CouponType.FIXED, 5000, null,
                                        ZonedDateTime.now().plusDays(60)))))
                .andExpect(status().isOk());
    }

    @Test
    void 쿠폰_템플릿_삭제_204() throws Exception {
        // given
        Long couponId = 쿠폰을_생성하고_ID를_반환한다("3000원 할인", CouponType.FIXED, 3000);

        // when & then
        mockMvc.perform(delete("/api-admin/v1/coupons/{couponId}", couponId))
                .andExpect(status().isNoContent());
    }

    @Test
    void 쿠폰_발급_201() throws Exception {
        // given
        Long couponId = 쿠폰을_생성하고_ID를_반환한다("3000원 할인", CouponType.FIXED, 3000);

        // when & then
        mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", couponId)
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", PASSWORD))
                .andExpect(status().isCreated());
    }

    @Test
    void 내_쿠폰_목록_조회_200() throws Exception {
        // given
        Long couponId = 쿠폰을_생성하고_ID를_반환한다("3000원 할인", CouponType.FIXED, 3000);
        쿠폰을_발급한다(couponId);

        // when & then
        mockMvc.perform(get("/api/v1/users/me/coupons")
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", PASSWORD))
                .andExpect(status().isOk());
    }

    @Test
    void 내_쿠폰_목록_조회_시_발급한_쿠폰_수_반환() throws Exception {
        // given
        Long couponId = 쿠폰을_생성하고_ID를_반환한다("3000원 할인", CouponType.FIXED, 3000);
        쿠폰을_발급한다(couponId);

        // when & then
        mockMvc.perform(get("/api/v1/users/me/coupons")
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", PASSWORD))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void 쿠폰_발급내역_조회_200() throws Exception {
        // given
        Long couponId = 쿠폰을_생성하고_ID를_반환한다("3000원 할인", CouponType.FIXED, 3000);
        쿠폰을_발급한다(couponId);

        // when & then
        mockMvc.perform(get("/api-admin/v1/coupons/{couponId}/issues", couponId))
                .andExpect(status().isOk());
    }

    @Test
    void 쿠폰_발급내역_조회_시_발급한_건수_반환() throws Exception {
        // given
        Long couponId = 쿠폰을_생성하고_ID를_반환한다("3000원 할인", CouponType.FIXED, 3000);
        쿠폰을_발급한다(couponId);

        // when & then
        mockMvc.perform(get("/api-admin/v1/coupons/{couponId}/issues", couponId))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void 만료된_쿠폰_발급_시_400() throws Exception {
        // given
        Long couponId = 만료된_쿠폰을_생성하고_ID를_반환한다();

        // when & then
        mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", couponId)
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", PASSWORD))
                .andExpect(status().isBadRequest());
    }

    private void 회원을_등록한다() throws Exception {
        mockMvc.perform(post("/api/members/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new MemberRegisterCommand(LOGIN_ID, PASSWORD, "테스터", LocalDate.of(2000, 1, 1), "coupon@test.com"))));
    }

    private void 쿠폰을_생성한다(String name, CouponType type, long value) throws Exception {
        mockMvc.perform(post("/api-admin/v1/coupons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new CouponCreateApiRequest(name, type, value, 10000L,
                                ZonedDateTime.now().plusDays(30)))));
    }

    private Long 쿠폰을_생성하고_ID를_반환한다(String name, CouponType type, long value) throws Exception {
        쿠폰을_생성한다(name, type, value);
        String response = mockMvc.perform(get("/api-admin/v1/coupons"))
                .andReturn().getResponse().getContentAsString();

        var coupons = objectMapper.readTree(response);
        for (var coupon : coupons) {
            if (coupon.get("name").asText().equals(name)) {
                return coupon.get("id").asLong();
            }
        }
        return coupons.get(0).get("id").asLong();
    }

    private Long 만료된_쿠폰을_생성하고_ID를_반환한다() throws Exception {
        mockMvc.perform(post("/api-admin/v1/coupons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new CouponCreateApiRequest("만료쿠폰", CouponType.FIXED, 3000, null,
                                ZonedDateTime.now().minusDays(1)))));

        String response = mockMvc.perform(get("/api-admin/v1/coupons"))
                .andReturn().getResponse().getContentAsString();

        var coupons = objectMapper.readTree(response);
        for (var coupon : coupons) {
            if (coupon.get("name").asText().equals("만료쿠폰")) {
                return coupon.get("id").asLong();
            }
        }
        return coupons.get(0).get("id").asLong();
    }

    private void 쿠폰을_발급한다(Long couponId) throws Exception {
        mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", couponId)
                .header("X-Loopers-LoginId", LOGIN_ID)
                .header("X-Loopers-LoginPw", PASSWORD));
    }
}
