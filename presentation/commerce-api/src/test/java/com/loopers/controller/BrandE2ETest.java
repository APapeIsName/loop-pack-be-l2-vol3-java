package com.loopers.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.interfaces.api.brand.dto.BrandCreateApiRequest;
import com.loopers.interfaces.api.brand.dto.BrandUpdateApiRequest;
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
class BrandE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void 브랜드_생성_성공_201() throws Exception {
        // when & then
        mockMvc.perform(post("/api/admin/brands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BrandCreateApiRequest("나이키"))))
                .andExpect(status().isCreated());
    }

    @Test
    void 브랜드_중복_생성_409() throws Exception {
        // given
        브랜드를_생성한다("나이키");

        // when & then
        mockMvc.perform(post("/api/admin/brands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BrandCreateApiRequest("나이키"))))
                .andExpect(status().isConflict());
    }

    @Test
    void 브랜드_단건_조회_200() throws Exception {
        // given
        Long brandId = 브랜드를_생성하고_ID를_반환한다("나이키");

        // when & then
        mockMvc.perform(get("/api/admin/brands/{id}", brandId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("나이키"));
    }

    @Test
    void 활성_브랜드_목록_조회_200() throws Exception {
        // given
        브랜드를_생성한다("나이키");
        브랜드를_생성한다("아디다스");

        // when & then
        mockMvc.perform(get("/api/brands"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void Admin_전체_브랜드_목록_조회_삭제_포함() throws Exception {
        // given
        Long brandId = 브랜드를_생성하고_ID를_반환한다("나이키");
        브랜드를_생성한다("아디다스");
        mockMvc.perform(delete("/api/admin/brands/{id}", brandId));

        // when & then
        mockMvc.perform(get("/api/admin/brands"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void 브랜드_수정_200() throws Exception {
        // given
        Long brandId = 브랜드를_생성하고_ID를_반환한다("나이키");

        // when & then
        mockMvc.perform(put("/api/admin/brands/{id}", brandId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BrandUpdateApiRequest("아디다스"))))
                .andExpect(status().isOk());
    }

    @Test
    void 브랜드_삭제_204() throws Exception {
        // given
        Long brandId = 브랜드를_생성하고_ID를_반환한다("나이키");

        // when & then
        mockMvc.perform(delete("/api/admin/brands/{id}", brandId))
                .andExpect(status().isNoContent());
    }

    @Test
    void 삭제된_브랜드는_활성_목록에_미포함() throws Exception {
        // given
        Long brandId = 브랜드를_생성하고_ID를_반환한다("나이키");
        브랜드를_생성한다("아디다스");
        mockMvc.perform(delete("/api/admin/brands/{id}", brandId));

        // when & then
        mockMvc.perform(get("/api/brands"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    private void 브랜드를_생성한다(String name) throws Exception {
        mockMvc.perform(post("/api/admin/brands")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BrandCreateApiRequest(name))));
    }

    private Long 브랜드를_생성하고_ID를_반환한다(String name) throws Exception {
        브랜드를_생성한다(name);
        String response = mockMvc.perform(get("/api/admin/brands"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get(0).get("id").asLong();
    }
}
