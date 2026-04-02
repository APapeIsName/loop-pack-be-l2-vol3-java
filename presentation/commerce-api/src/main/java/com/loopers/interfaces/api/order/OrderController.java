package com.loopers.interfaces.api.order;

import com.loopers.application.service.MemberService;
import com.loopers.application.service.OrderQueueService;
import com.loopers.application.service.OrderService;
import com.loopers.application.service.dto.MemberInfo;
import com.loopers.application.service.dto.OrderCreateCommand;
import com.loopers.interfaces.api.order.dto.OrderApiResponse;
import com.loopers.interfaces.api.order.dto.OrderCreateApiRequest;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderQueueService orderQueueService;
    private final MemberService memberService;
    private final BulkheadRegistry bulkheadRegistry;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderApiResponse create(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String password,
            @RequestHeader(value = "X-Entry-Token", required = false) String entryToken,
            @RequestBody OrderCreateApiRequest request
    ) {
        MemberInfo member = memberService.getMyInfo(loginId, password);
        OrderCreateCommand command = request.toCommand(member.memberId());

        boolean isQueueOrder = command.orderLines().stream()
                .anyMatch(line -> orderQueueService.isQueueActiveProduct(line.productId()));

        if (isQueueOrder) {
            return OrderApiResponse.from(
                    bulkheadRegistry.bulkhead("queue-order")
                            .executeSupplier(() -> orderQueueService.createOrderWithQueue(command, entryToken)));
        }

        return OrderApiResponse.from(orderService.create(command));
    }

    @GetMapping
    public List<OrderApiResponse> getMyOrders(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String password
    ) {
        MemberInfo member = memberService.getMyInfo(loginId, password);
        return orderService.getByMemberId(member.memberId()).stream()
                .map(OrderApiResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public OrderApiResponse getById(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String password,
            @PathVariable Long id
    ) {
        MemberInfo member = memberService.getMyInfo(loginId, password);
        return OrderApiResponse.from(orderService.getById(id, member.memberId()));
    }
}
