package com.loopers.interfaces.api.order;

import com.loopers.application.service.OrderService;
import com.loopers.interfaces.api.order.dto.OrderApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 주문 관리 API (관리자)
 */
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;

    /** 주문 전체 조회 */
    @GetMapping
    public List<OrderApiResponse> getAll() {
        return orderService.getAll().stream()
                .map(OrderApiResponse::from)
                .toList();
    }

    /** 주문 단건 조회 */
    @GetMapping("/{id}")
    public OrderApiResponse getById(@PathVariable Long id) {
        return OrderApiResponse.from(orderService.getByIdForAdmin(id));
    }
}
