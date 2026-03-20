package com.loopers.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.service.dto.MemberRegisterCommand;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.gateway.PaymentGatewayResponse;
import com.loopers.domain.payment.gateway.PaymentGatewayStatusResponse;
import com.loopers.interfaces.api.brand.dto.BrandCreateApiRequest;
import com.loopers.interfaces.api.order.dto.OrderCreateApiRequest;
import com.loopers.interfaces.api.order.dto.OrderLineItemRequest;
import com.loopers.interfaces.api.payment.dto.PaymentCallbackApiRequest;
import com.loopers.interfaces.api.payment.dto.PaymentCreateApiRequest;
import com.loopers.interfaces.api.product.dto.ProductCreateApiRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PaymentE2ETest {

    private static final String LOGIN_ID = "paytest123";
    private static final String PASSWORD = "Pay!12345";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PaymentGateway paymentGateway;

    private Long orderId;

    @BeforeEach
    void setUp() throws Exception {
        회원을_등록한다();
        Long brandId = 브랜드를_생성하고_ID를_반환한다("나이키");
        Long productId = 상품을_생성하고_ID를_반환한다("에어맥스", 100000, 50, brandId);
        orderId = 주문을_생성하고_ID를_반환한다(List.of(new OrderLineItemRequest(productId, 2)));
    }

    @Test
    void 결제_요청_201() throws Exception {
        // given
        given(paymentGateway.requestPayment(any(), any()))
                .willReturn(PaymentGatewayResponse.success("TR:test123"));

        // when & then
        mockMvc.perform(post("/api/v1/payments")
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", PASSWORD)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PaymentCreateApiRequest(orderId, "SAMSUNG", "1234-5678-9012-3456"))))
                .andExpect(status().isCreated());
    }

    @Test
    void 결제_요청_성공_시_PENDING_상태() throws Exception {
        // given
        given(paymentGateway.requestPayment(any(), any()))
                .willReturn(PaymentGatewayResponse.success("TR:test123"));

        // when & then
        mockMvc.perform(post("/api/v1/payments")
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", PASSWORD)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PaymentCreateApiRequest(orderId, "SAMSUNG", "1234-5678-9012-3456"))))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void 결제_요청_PG_거절_시_FAILED() throws Exception {
        // given
        given(paymentGateway.requestPayment(any(), any()))
                .willReturn(PaymentGatewayResponse.fail("한도 초과"));

        // when & then
        mockMvc.perform(post("/api/v1/payments")
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", PASSWORD)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PaymentCreateApiRequest(orderId, "SAMSUNG", "1234-5678-9012-3456"))))
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    void 콜백_성공_시_200() throws Exception {
        // given
        given(paymentGateway.requestPayment(any(), any()))
                .willReturn(PaymentGatewayResponse.success("TR:callback1"));
        결제를_요청한다(orderId);

        // when & then
        mockMvc.perform(post("/api/v1/payments/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PaymentCallbackApiRequest("TR:callback1", "SUCCESS", null))))
                .andExpect(status().isOk());
    }

    @Test
    void 콜백_성공_후_주문_상태_PAID() throws Exception {
        // given
        given(paymentGateway.requestPayment(any(), any()))
                .willReturn(PaymentGatewayResponse.success("TR:callback2"));
        결제를_요청한다(orderId);

        mockMvc.perform(post("/api/v1/payments/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new PaymentCallbackApiRequest("TR:callback2", "SUCCESS", null))));

        // when & then
        mockMvc.perform(get("/api/orders/{id}", orderId)
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", PASSWORD))
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void 콜백_실패_시에도_200() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/payments/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PaymentCallbackApiRequest("TR:nonexistent", "SUCCESS", null))))
                .andExpect(status().isOk());
    }

    @Test
    void 수동_reconcile_200() throws Exception {
        // given
        given(paymentGateway.requestPayment(any(), any()))
                .willReturn(PaymentGatewayResponse.success("TR:reconcile1"));
        given(paymentGateway.getPaymentStatus(any(), any()))
                .willReturn(new PaymentGatewayStatusResponse("TR:reconcile1", String.valueOf(orderId), "SUCCESS", null));

        Long paymentId = 결제를_요청하고_ID를_반환한다(orderId);

        // when & then
        mockMvc.perform(post("/api/admin/payments/{id}/reconcile", paymentId))
                .andExpect(status().isOk());
    }

    private void 회원을_등록한다() throws Exception {
        mockMvc.perform(post("/api/members/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new MemberRegisterCommand(LOGIN_ID, PASSWORD, "테스터", LocalDate.of(2000, 1, 1), "pay@test.com"))));
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

    private Long 주문을_생성하고_ID를_반환한다(List<OrderLineItemRequest> items) throws Exception {
        String response = mockMvc.perform(post("/api/orders")
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", PASSWORD)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderCreateApiRequest(items, null))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private void 결제를_요청한다(Long orderId) throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                .header("X-Loopers-LoginId", LOGIN_ID)
                .header("X-Loopers-LoginPw", PASSWORD)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new PaymentCreateApiRequest(orderId, "SAMSUNG", "1234-5678-9012-3456"))));
    }

    private Long 결제를_요청하고_ID를_반환한다(Long orderId) throws Exception {
        String response = mockMvc.perform(post("/api/v1/payments")
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", PASSWORD)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PaymentCreateApiRequest(orderId, "SAMSUNG", "1234-5678-9012-3456"))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("paymentId").asLong();
    }
}
