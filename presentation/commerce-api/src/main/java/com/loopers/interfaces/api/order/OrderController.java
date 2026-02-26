package com.loopers.interfaces.api.order;

import com.loopers.application.service.MemberService;
import com.loopers.application.service.OrderService;
import com.loopers.application.service.dto.MemberInfo;
import com.loopers.interfaces.api.order.dto.OrderApiResponse;
import com.loopers.interfaces.api.order.dto.OrderCreateApiRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 주문 API
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final MemberService memberService;

    /** 주문 생성 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderApiResponse create(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String password,
            @RequestBody OrderCreateApiRequest request
    ) {
        MemberInfo member = memberService.getMyInfo(loginId, password);
        return OrderApiResponse.from(orderService.create(request.toCommand(member.memberId())));
    }

    /** 내 주문 목록 조회 */
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

    /** 주문 단건 조회 */
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
