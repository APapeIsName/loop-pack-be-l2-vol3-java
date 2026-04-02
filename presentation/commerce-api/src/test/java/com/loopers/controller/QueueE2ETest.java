package com.loopers.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.service.OrderQueueService;
import com.loopers.application.service.dto.MemberRegisterCommand;
import com.loopers.interfaces.api.brand.dto.BrandCreateApiRequest;
import com.loopers.interfaces.api.order.dto.OrderCreateApiRequest;
import com.loopers.interfaces.api.order.dto.OrderLineItemRequest;
import com.loopers.interfaces.api.product.dto.ProductCreateApiRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class QueueE2ETest {

    private static final String LOGIN_ID = "queuetest123";
    private static final String PASSWORD = "Queue!1234";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderQueueService orderQueueService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private Long productId;

    @BeforeEach
    void setUp() throws Exception {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        회원을_등록한다();
        Long brandId = 브랜드를_생성하고_ID를_반환한다("나이키");
        productId = 상품을_생성하고_ID를_반환한다("에어맥스", 100000, 50, brandId);
    }

    @Test
    void 대기열_진입_시_201_응답() throws Exception {
        // given
        대기열을_활성화한다(productId);

        // when & then
        mockMvc.perform(post("/api/queue/products/{productId}/enter", productId)
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", PASSWORD))
                .andExpect(status().isCreated());
    }

    @Test
    void 대기열_진입_시_순번을_반환한다() throws Exception {
        // given
        대기열을_활성화한다(productId);

        // when & then
        mockMvc.perform(post("/api/queue/products/{productId}/enter", productId)
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", PASSWORD))
                .andExpect(jsonPath("$.position").value(0));
    }

    @Test
    void 비활성_상품에_진입하면_400() throws Exception {
        // when & then
        mockMvc.perform(post("/api/queue/products/{productId}/enter", productId)
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", PASSWORD))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 순번_조회_시_200_응답() throws Exception {
        // given
        대기열을_활성화한다(productId);
        대기열에_진입한다(productId);

        // when & then
        mockMvc.perform(get("/api/queue/products/{productId}/position", productId)
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", PASSWORD))
                .andExpect(status().isOk());
    }

    @Test
    void 대기열에_없는_유저가_순번_조회하면_404() throws Exception {
        // given
        대기열을_활성화한다(productId);

        // when & then
        mockMvc.perform(get("/api/queue/products/{productId}/position", productId)
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", PASSWORD))
                .andExpect(status().isNotFound());
    }

    @Test
    void 대기열_이탈_시_204_응답() throws Exception {
        // given
        대기열을_활성화한다(productId);
        대기열에_진입한다(productId);

        // when & then
        mockMvc.perform(delete("/api/queue/products/{productId}/exit", productId)
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", PASSWORD))
                .andExpect(status().isNoContent());
    }

    @Test
    void 대기열_활성화_시_201() throws Exception {
        // when & then
        mockMvc.perform(post("/api/admin/queue/products/{productId}/activate", productId))
                .andExpect(status().isCreated());
    }

    @Test
    void 대기열_비활성화_시_204() throws Exception {
        // given
        대기열을_활성화한다(productId);

        // when & then
        mockMvc.perform(delete("/api/admin/queue/products/{productId}/deactivate", productId))
                .andExpect(status().isNoContent());
    }

    @Test
    void 대기열_상품_주문_시_토큰_없으면_403() throws Exception {
        // given
        대기열을_활성화한다(productId);

        // when & then
        mockMvc.perform(post("/api/orders")
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", PASSWORD)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new OrderCreateApiRequest(List.of(
                                        new OrderLineItemRequest(productId, 1)
                                ), null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void 토큰이_있으면_주문_성공_201() throws Exception {
        // given
        대기열을_활성화한다(productId);
        대기열에_진입한다(productId);
        orderQueueService.processAllQueues();
        String token = 순번_조회로_토큰을_반환한다(productId);

        // when & then
        mockMvc.perform(post("/api/orders")
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", PASSWORD)
                        .header("X-Entry-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new OrderCreateApiRequest(List.of(
                                        new OrderLineItemRequest(productId, 1)
                                ), null))))
                .andExpect(status().isCreated());
    }

    private void 회원을_등록한다() throws Exception {
        mockMvc.perform(post("/api/members/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new MemberRegisterCommand(LOGIN_ID, PASSWORD, "테스터", LocalDate.of(2000, 1, 1), "queue@test.com"))));
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

    private void 대기열을_활성화한다(Long productId) throws Exception {
        mockMvc.perform(post("/api/admin/queue/products/{productId}/activate", productId));
    }

    private void 대기열에_진입한다(Long productId) throws Exception {
        mockMvc.perform(post("/api/queue/products/{productId}/enter", productId)
                .header("X-Loopers-LoginId", LOGIN_ID)
                .header("X-Loopers-LoginPw", PASSWORD));
    }

    private String 순번_조회로_토큰을_반환한다(Long productId) throws Exception {
        String response = mockMvc.perform(get("/api/queue/products/{productId}/position", productId)
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", PASSWORD))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }
}
