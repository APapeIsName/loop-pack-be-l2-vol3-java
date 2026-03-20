package com.loopers.interfaces.api.payment;

import com.loopers.application.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/payments")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final PaymentService paymentService;

    @PostMapping("/{id}/reconcile")
    public void reconcile(@PathVariable Long id) {
        paymentService.reconcile(id);
    }
}
