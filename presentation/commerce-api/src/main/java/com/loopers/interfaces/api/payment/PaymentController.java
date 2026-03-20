package com.loopers.interfaces.api.payment;

import com.loopers.application.service.MemberService;
import com.loopers.application.service.PaymentService;
import com.loopers.application.service.dto.MemberInfo;
import com.loopers.interfaces.api.payment.dto.PaymentApiResponse;
import com.loopers.interfaces.api.payment.dto.PaymentCreateApiRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final MemberService memberService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentApiResponse create(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String password,
            @RequestBody PaymentCreateApiRequest request
    ) {
        MemberInfo member = memberService.getMyInfo(loginId, password);
        return PaymentApiResponse.from(paymentService.requestPayment(request.toCommand(member.memberId())));
    }

    @GetMapping("/orders/{orderId}")
    public PaymentApiResponse getByOrderId(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String password,
            @PathVariable Long orderId
    ) {
        MemberInfo member = memberService.getMyInfo(loginId, password);
        return PaymentApiResponse.from(paymentService.getByOrderId(orderId, member.memberId()));
    }
}
