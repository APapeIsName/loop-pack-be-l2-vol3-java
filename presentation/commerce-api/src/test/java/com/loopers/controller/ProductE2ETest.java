package com.loopers.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.interfaces.api.brand.dto.BrandCreateApiRequest;
import com.loopers.interfaces.api.product.dto.ProductCreateApiRequest;
import com.loopers.interfaces.api.product.dto.ProductUpdateApiRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void 상품_생성_성공_201() throws Exception {
        // given
        Long brandId = 브랜드를_생성하고_ID를_반환한다("나이키");

        // when & then
        mockMvc.perform(post("/api/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ProductCreateApiRequest("에어맥스", "설명", 100000, 50, brandId))))
                .andExpect(status().isCreated());
    }

    @Test
    void 상품_상세_조회_브랜드명_포함() throws Exception {
        // given
        Long brandId = 브랜드를_생성하고_ID를_반환한다("나이키");
        Long productId = 상품을_생성하고_ID를_반환한다("에어맥스", 100000, 50, brandId);

        // when & then
        mockMvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brandName").value("나이키"))
                .andExpect(jsonPath("$.likesCount").value(0));
    }

    @Test
    void 활성_상품_목록_조회_정렬() throws Exception {
        // given
        Long brandId = 브랜드를_생성하고_ID를_반환한다("나이키");
        상품을_생성한다("에어맥스", 100000, 50, brandId);
        상품을_생성한다("조던", 200000, 30, brandId);

        // when & then
        mockMvc.perform(get("/api/products").param("sort", "PRICE_ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("에어맥스"));
    }

    @Test
    void 상품_수정_200() throws Exception {
        // given
        Long brandId = 브랜드를_생성하고_ID를_반환한다("나이키");
        Long productId = 상품을_생성하고_ID를_반환한다("에어맥스", 100000, 50, brandId);

        // when & then
        mockMvc.perform(put("/api/admin/products/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ProductUpdateApiRequest("에어맥스2", "새설명", 120000, 60))))
                .andExpect(status().isOk());
    }

    @Test
    void 상품_삭제_204() throws Exception {
        // given
        Long brandId = 브랜드를_생성하고_ID를_반환한다("나이키");
        Long productId = 상품을_생성하고_ID를_반환한다("에어맥스", 100000, 50, brandId);

        // when & then
        mockMvc.perform(delete("/api/admin/products/{id}", productId))
                .andExpect(status().isNoContent());
    }

    @Test
    void 삭제된_상품은_활성_목록에_미포함() throws Exception {
        // given
        Long brandId = 브랜드를_생성하고_ID를_반환한다("나이키");
        Long productId = 상품을_생성하고_ID를_반환한다("에어맥스", 100000, 50, brandId);
        상품을_생성한다("조던", 200000, 30, brandId);
        mockMvc.perform(delete("/api/admin/products/{id}", productId));

        // when & then
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    private Long 브랜드를_생성하고_ID를_반환한다(String name) throws Exception {
        mockMvc.perform(post("/api/admin/brands")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BrandCreateApiRequest(name))));

        String response = mockMvc.perform(get("/api/admin/brands"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get(0).get("id").asLong();
    }

    private void 상품을_생성한다(String name, long price, long stock, Long brandId) throws Exception {
        mockMvc.perform(post("/api/admin/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new ProductCreateApiRequest(name, "설명", price, stock, brandId))));
    }

    private Long 상품을_생성하고_ID를_반환한다(String name, long price, long stock, Long brandId) throws Exception {
        상품을_생성한다(name, price, stock, brandId);
        String response = mockMvc.perform(get("/api/admin/products"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get(0).get("id").asLong();
    }
}
