package com.loopers.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.service.dto.MemberRegisterCommand;
import com.loopers.interfaces.api.brand.dto.BrandCreateApiRequest;
import com.loopers.interfaces.api.product.dto.ProductCreateApiRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LikeE2ETest {

    private static final String LOGIN_ID = "liketest123";
    private static final String PASSWORD = "Like!1234";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long brandId;
    private Long productId;

    @BeforeEach
    void setUp() throws Exception {
        회원을_등록한다();
        brandId = 브랜드를_생성하고_ID를_반환한다("나이키");
        productId = 상품을_생성하고_ID를_반환한다("에어맥스", 100000, 50, brandId);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DELETE FROM likes");
        jdbcTemplate.execute("DELETE FROM product");
        jdbcTemplate.execute("DELETE FROM brand");
        jdbcTemplate.execute("DELETE FROM member");
    }

    @Test
    void 좋아요_등록_201() throws Exception {
        // when & then
        mockMvc.perform(post("/api/products/{productId}/likes", productId)
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", PASSWORD))
                .andExpect(status().isCreated());
    }

    @Test
    void 좋아요_등록_후_상품_조회_200() throws Exception {
        // given
        좋아요를_등록한다(productId);

        // when & then
        mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(status().isOk());
    }

    @Test
    void 좋아요_등록_시_likesCount_증가() throws Exception {
        // given
        좋아요를_등록한다(productId);

        // when & then
        mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(jsonPath("$.likesCount").value(1));
    }

    @Test
    void 좋아요_중복_등록_시_400() throws Exception {
        // given
        좋아요를_등록한다(productId);

        // when & then
        mockMvc.perform(post("/api/products/{productId}/likes", productId)
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", PASSWORD))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 삭제된_상품에_좋아요_시_404() throws Exception {
        // given
        상품을_삭제한다(productId);

        // when & then
        mockMvc.perform(post("/api/products/{productId}/likes", productId)
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", PASSWORD))
                .andExpect(status().isNotFound());
    }

    @Test
    void 브랜드_삭제된_상품에_좋아요_시_404() throws Exception {
        // given
        브랜드를_삭제한다(brandId);

        // when & then
        mockMvc.perform(post("/api/products/{productId}/likes", productId)
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", PASSWORD))
                .andExpect(status().isNotFound());
    }

    @Test
    void 좋아요_취소_200() throws Exception {
        // given
        좋아요를_등록한다(productId);

        // when & then
        mockMvc.perform(delete("/api/products/{productId}/likes", productId)
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", PASSWORD))
                .andExpect(status().isOk());
    }

    @Test
    void 좋아요_취소_후_상품_조회_200() throws Exception {
        // given
        좋아요를_등록한다(productId);
        좋아요를_취소한다(productId);

        // when & then
        mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(status().isOk());
    }

    @Test
    void 좋아요_취소_시_likesCount_감소() throws Exception {
        // given
        좋아요를_등록한다(productId);
        좋아요를_취소한다(productId);

        // when & then
        mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(jsonPath("$.likesCount").value(0));
    }

    @Test
    void 좋아요하지_않은_상품_취소_시_400() throws Exception {
        // when & then
        mockMvc.perform(delete("/api/products/{productId}/likes", productId)
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", PASSWORD))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 내_좋아요_목록_조회_200() throws Exception {
        // given
        Long productId2 = 상품을_생성하고_ID를_반환한다("조던", 200000, 30, brandId);
        좋아요를_등록한다(productId);
        좋아요를_등록한다(productId2);

        // when & then
        mockMvc.perform(get("/api/likes")
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", PASSWORD))
                .andExpect(status().isOk());
    }

    @Test
    void 내_좋아요_목록_조회_시_좋아요한_상품_수_반환() throws Exception {
        // given
        Long productId2 = 상품을_생성하고_ID를_반환한다("조던", 200000, 30, brandId);
        좋아요를_등록한다(productId);
        좋아요를_등록한다(productId2);

        // when & then
        mockMvc.perform(get("/api/likes")
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", PASSWORD))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void 삭제된_상품은_좋아요_목록에서_제외_200() throws Exception {
        // given
        Long productId2 = 상품을_생성하고_ID를_반환한다("조던", 200000, 30, brandId);
        좋아요를_등록한다(productId);
        좋아요를_등록한다(productId2);
        상품을_삭제한다(productId2);

        // when & then
        mockMvc.perform(get("/api/likes")
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", PASSWORD))
                .andExpect(status().isOk());
    }

    @Test
    void 삭제된_상품은_좋아요_목록에서_제외() throws Exception {
        // given
        Long productId2 = 상품을_생성하고_ID를_반환한다("조던", 200000, 30, brandId);
        좋아요를_등록한다(productId);
        좋아요를_등록한다(productId2);
        상품을_삭제한다(productId2);

        // when & then
        mockMvc.perform(get("/api/likes")
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", PASSWORD))
                .andExpect(jsonPath("$.length()").value(1));
    }

    private void 회원을_등록한다() throws Exception {
        mockMvc.perform(post("/api/members/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new MemberRegisterCommand(LOGIN_ID, PASSWORD, "테스터", LocalDate.of(2000, 1, 1), "like@test.com"))));
    }

    private Long 브랜드를_생성하고_ID를_반환한다(String name) throws Exception {
        mockMvc.perform(post("/api/admin/brands")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BrandCreateApiRequest(name))));

        String response = mockMvc.perform(get("/api/admin/brands"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get(0).get("id").asLong();
    }

    private Long 상품을_생성하고_ID를_반환한다(String name, long price, long stock, Long brandId) throws Exception {
        mockMvc.perform(post("/api/admin/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new ProductCreateApiRequest(name, "설명", price, stock, brandId))));

        String response = mockMvc.perform(get("/api/admin/products"))
                .andReturn().getResponse().getContentAsString();

        var products = objectMapper.readTree(response);
        for (var product : products) {
            if (product.get("name").asText().equals(name)) {
                return product.get("id").asLong();
            }
        }
        return products.get(0).get("id").asLong();
    }

    private void 좋아요를_등록한다(Long productId) throws Exception {
        mockMvc.perform(post("/api/products/{productId}/likes", productId)
                .header("X-Loopers-LoginId", LOGIN_ID)
                .header("X-Loopers-LoginPw", PASSWORD));
    }

    private void 좋아요를_취소한다(Long productId) throws Exception {
        mockMvc.perform(delete("/api/products/{productId}/likes", productId)
                .header("X-Loopers-LoginId", LOGIN_ID)
                .header("X-Loopers-LoginPw", PASSWORD));
    }

    private void 상품을_삭제한다(Long productId) throws Exception {
        mockMvc.perform(delete("/api/admin/products/{id}", productId));
    }

    private void 브랜드를_삭제한다(Long brandId) throws Exception {
        mockMvc.perform(delete("/api/admin/brands/{id}", brandId));
    }
}
