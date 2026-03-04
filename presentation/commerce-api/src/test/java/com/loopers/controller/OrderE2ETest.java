package com.loopers.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderE2ETest {

    private static final String LOGIN_ID = "ordertest123";
    private static final String PASSWORD = "Order!1234";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private Long brandId;
    private Long productId1;
    private Long productId2;

    @BeforeEach
    void setUp() throws Exception {
        회원을_등록한다();
        brandId = 브랜드를_생성하고_ID를_반환한다("나이키");
        productId1 = 상품을_생성하고_ID를_반환한다("에어맥스", 100000, 50, brandId);
        productId2 = 상품을_생성하고_ID를_반환한다("조던", 200000, 30, brandId);
    }

    @Test
    void 주문_생성_수락_201() throws Exception {
        // when & then
        mockMvc.perform(post("/api/orders")
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", PASSWORD)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new OrderCreateApiRequest(List.of(
                                        new OrderLineItemRequest(productId1, 2),
                                        new OrderLineItemRequest(productId2, 1)
                                ), null))))
                .andExpect(status().isCreated());
    }

    @Test
    void 주문_생성_재고_충분하면_수락_상태() throws Exception {
        // when & then
        mockMvc.perform(post("/api/orders")
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", PASSWORD)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new OrderCreateApiRequest(List.of(
                                        new OrderLineItemRequest(productId1, 2),
                                        new OrderLineItemRequest(productId2, 1)
                                ), null))))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void 주문_생성_거절_재고_부족_201() throws Exception {
        // when & then
        mockMvc.perform(post("/api/orders")
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", PASSWORD)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new OrderCreateApiRequest(List.of(
                                        new OrderLineItemRequest(productId1, 999)
                                ), null))))
                .andExpect(status().isCreated());
    }

    @Test
    void 주문_생성_재고_부족하면_거절_상태() throws Exception {
        // when & then
        mockMvc.perform(post("/api/orders")
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", PASSWORD)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new OrderCreateApiRequest(List.of(
                                        new OrderLineItemRequest(productId1, 999)
                                ), null))))
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void 주문_생성_시_스냅샷_상품명_포함() throws Exception {
        // when & then
        mockMvc.perform(post("/api/orders")
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", PASSWORD)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new OrderCreateApiRequest(List.of(
                                        new OrderLineItemRequest(productId1, 1)
                                ), null))))
                .andExpect(jsonPath("$.orderLines[0].productName").value("에어맥스"));
    }

    @Test
    void 주문_생성_시_스냅샷_브랜드명_포함() throws Exception {
        // when & then
        mockMvc.perform(post("/api/orders")
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", PASSWORD)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new OrderCreateApiRequest(List.of(
                                        new OrderLineItemRequest(productId1, 1)
                                ), null))))
                .andExpect(jsonPath("$.orderLines[0].brandName").value("나이키"));
    }

    @Test
    void 내_주문_내역_조회_200() throws Exception {
        // given
        주문을_생성한다(List.of(new OrderLineItemRequest(productId1, 1)));

        // when & then
        mockMvc.perform(get("/api/orders")
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", PASSWORD))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void 주문_상세_조회_200() throws Exception {
        // given
        Long orderId = 주문을_생성하고_ID를_반환한다(List.of(new OrderLineItemRequest(productId1, 2)));

        // when & then
        mockMvc.perform(get("/api/orders/{id}", orderId)
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", PASSWORD))
                .andExpect(jsonPath("$.orderLines.length()").value(1));
    }

    @Test
    void 관리자_전체_목록_200() throws Exception {
        // given
        주문을_생성한다(List.of(new OrderLineItemRequest(productId1, 1)));
        주문을_생성한다(List.of(new OrderLineItemRequest(productId2, 1)));

        // when & then
        mockMvc.perform(get("/api/admin/orders"))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void 관리자_상세_조회_200() throws Exception {
        // given
        Long orderId = 주문을_생성하고_ID를_반환한다(List.of(new OrderLineItemRequest(productId1, 1)));

        // when & then
        mockMvc.perform(get("/api/admin/orders/{id}", orderId))
                .andExpect(jsonPath("$.id").value(orderId));
    }

    private void 회원을_등록한다() throws Exception {
        mockMvc.perform(post("/api/members/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new MemberRegisterCommand(LOGIN_ID, PASSWORD, "테스터", LocalDate.of(2000, 1, 1), "order@test.com"))));
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

    private void 주문을_생성한다(List<OrderLineItemRequest> items) throws Exception {
        mockMvc.perform(post("/api/orders")
                .header("X-Loopers-LoginId", LOGIN_ID)
                .header("X-Loopers-LoginPw", PASSWORD)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new OrderCreateApiRequest(items, null))));
    }

    private Long 주문을_생성하고_ID를_반환한다(List<OrderLineItemRequest> items) throws Exception {
        String response = mockMvc.perform(post("/api/orders")
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", PASSWORD)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderCreateApiRequest(items, null))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }
}
