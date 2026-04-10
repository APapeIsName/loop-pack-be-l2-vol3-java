package com.loopers.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.ranking.ProductRankingRepository;
import com.loopers.interfaces.api.brand.dto.BrandCreateApiRequest;
import com.loopers.interfaces.api.product.dto.ProductCreateApiRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RankingE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRankingRepository productRankingRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final String TODAY = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    private static final String YESTERDAY = LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));

    @AfterEach
    void tearDown() {
        redisTemplate.delete("ranking:daily:" + TODAY);
        redisTemplate.delete("ranking:daily:" + YESTERDAY);
    }

    @Test
    void 랭킹_조회_성공_200() throws Exception {
        // given
        Long brandId = 브랜드를_생성하고_ID를_반환한다("나이키");
        Long productId = 상품을_생성하고_ID를_반환한다("에어맥스", 100000, 50, brandId);
        productRankingRepository.incrementScore(productId, 100.0, TODAY);

        // when & then
        mockMvc.perform(get("/api/v1/rankings")
                        .param("date", TODAY)
                        .param("size", "20")
                        .param("page", "1"))
                .andExpect(status().isOk());
    }

    @Test
    void 랭킹_조회_시_상품정보가_포함된다() throws Exception {
        // given
        Long brandId = 브랜드를_생성하고_ID를_반환한다("나이키");
        Long productId = 상품을_생성하고_ID를_반환한다("에어맥스", 100000, 50, brandId);
        productRankingRepository.incrementScore(productId, 100.0, TODAY);

        // when & then
        mockMvc.perform(get("/api/v1/rankings")
                        .param("date", TODAY))
                .andExpect(jsonPath("$[0].productName").value("에어맥스"));
    }

    @Test
    void 랭킹_조회_시_브랜드명이_포함된다() throws Exception {
        // given
        Long brandId = 브랜드를_생성하고_ID를_반환한다("나이키");
        Long productId = 상품을_생성하고_ID를_반환한다("에어맥스", 100000, 50, brandId);
        productRankingRepository.incrementScore(productId, 100.0, TODAY);

        // when & then
        mockMvc.perform(get("/api/v1/rankings")
                        .param("date", TODAY))
                .andExpect(jsonPath("$[0].brandName").value("나이키"));
    }

    @Test
    void 가중치_적용_시_주문_1건이_좋아요_3건보다_높다() throws Exception {
        // given
        Long brandId = 브랜드를_생성하고_ID를_반환한다("나이키");
        Long product1 = 상품을_생성하고_ID를_반환한다("좋아요상품", 100000, 50, brandId);
        Long product2 = 상품을_생성하고_ID를_반환한다("주문상품", 100000, 50, brandId);

        productRankingRepository.incrementScore(product1, 3.0, TODAY);
        productRankingRepository.incrementScore(product1, 3.0, TODAY);
        productRankingRepository.incrementScore(product1, 3.0, TODAY);
        productRankingRepository.incrementScore(product2, 10.0, TODAY);

        // when & then
        mockMvc.perform(get("/api/v1/rankings")
                        .param("date", TODAY))
                .andExpect(jsonPath("$[0].productName").value("주문상품"));
    }

    @Test
    void 날짜_미지정_시_오늘_날짜로_조회된다() throws Exception {
        // given
        Long brandId = 브랜드를_생성하고_ID를_반환한다("나이키");
        Long productId = 상품을_생성하고_ID를_반환한다("에어맥스", 100000, 50, brandId);
        productRankingRepository.incrementScore(productId, 100.0, TODAY);

        // when & then
        mockMvc.perform(get("/api/v1/rankings"))
                .andExpect(jsonPath("$[0].productId").value(productId));
    }

    @Test
    void 이전_날짜의_랭킹을_조회할_수_있다() throws Exception {
        // given
        Long brandId = 브랜드를_생성하고_ID를_반환한다("나이키");
        Long productId = 상품을_생성하고_ID를_반환한다("에어맥스", 100000, 50, brandId);
        productRankingRepository.incrementScore(productId, 50.0, YESTERDAY);

        // when & then
        mockMvc.perform(get("/api/v1/rankings")
                        .param("date", YESTERDAY))
                .andExpect(jsonPath("$[0].productId").value(productId));
    }

    @Test
    void 상품_상세_조회_시_랭킹_순위가_포함된다() throws Exception {
        // given
        Long brandId = 브랜드를_생성하고_ID를_반환한다("나이키");
        Long productId = 상품을_생성하고_ID를_반환한다("에어맥스", 100000, 50, brandId);
        productRankingRepository.incrementScore(productId, 100.0, TODAY);

        // when & then
        mockMvc.perform(get("/api/products/{id}", productId))
                .andExpect(jsonPath("$.rankingPosition").value(1));
    }

    private Long 브랜드를_생성하고_ID를_반환한다(String name) throws Exception {
        mockMvc.perform(post("/api/admin/brands")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BrandCreateApiRequest(name))));

        String response = mockMvc.perform(get("/api/admin/brands"))
                .andReturn().getResponse().getContentAsString();
        var brands = objectMapper.readTree(response);
        for (var brand : brands) {
            if (brand.get("name").asText().equals(name)) {
                return brand.get("id").asLong();
            }
        }
        throw new IllegalStateException("브랜드 생성 실패: " + name);
    }

    private Long 상품을_생성하고_ID를_반환한다(String name, long price, long stock, Long brandId) throws Exception {
        mockMvc.perform(post("/api/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ProductCreateApiRequest(name, "설명", price, stock, brandId))))
                .andExpect(status().isCreated());

        String response = mockMvc.perform(get("/api/admin/products"))
                .andReturn().getResponse().getContentAsString();
        var products = objectMapper.readTree(response);
        for (var product : products) {
            if (product.get("name").asText().equals(name)) {
                return product.get("id").asLong();
            }
        }
        throw new IllegalStateException("상품 생성 실패: " + name);
    }
}
