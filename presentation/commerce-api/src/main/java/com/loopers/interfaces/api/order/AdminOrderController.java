package com.loopers.interfaces.api.order;

import com.loopers.application.service.OrderService;
import com.loopers.interfaces.api.order.dto.OrderApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;

    @GetMapping
    public List<OrderApiResponse> getAll() {
        return orderService.getAll().stream()
                .map(OrderApiResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public OrderApiResponse getById(@PathVariable Long id) {
        return OrderApiResponse.from(orderService.getByIdForAdmin(id));
    }
}
