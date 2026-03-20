package com.loopers.application.service;

import com.loopers.application.service.dto.PaymentCallbackCommand;
import com.loopers.application.service.dto.PaymentInfo;
import com.loopers.application.service.dto.PaymentRequestCommand;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderExceptionMessage;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.payment.*;
import com.loopers.domain.payment.gateway.PaymentGatewayRequest;
import com.loopers.domain.payment.gateway.PaymentGatewayResponse;
import com.loopers.domain.payment.gateway.PaymentGatewayStatusResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentGateway paymentGateway;
    private final TransactionTemplate transactionTemplate;

    public PaymentInfo requestPayment(PaymentRequestCommand command) {
        Payment payment = transactionTemplate.execute(status -> {
            Order order = orderRepository.findByIdWithPessimisticLock(command.orderId())
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                            OrderExceptionMessage.Order.NOT_FOUND.message()));

            if (!order.isOwnedBy(command.memberId())) {
                throw new CoreException(ErrorType.FORBIDDEN,
                        OrderExceptionMessage.Order.NOT_OWNER.message());
            }
            if (!order.isAccepted()) {
                throw new CoreException(ErrorType.CONFLICT,
                        PaymentExceptionMessage.Payment.ORDER_NOT_ACCEPTED.message());
            }

            paymentRepository.findByOrderIdAndStatus(command.orderId(), PaymentStatus.PENDING)
                    .ifPresent(p -> {
                        throw new CoreException(ErrorType.CONFLICT,
                                PaymentExceptionMessage.Payment.DUPLICATE_PAYMENT.message());
                    });

            return paymentRepository.save(Payment.request(
                    command.orderId(), command.memberId(),
                    command.cardType(), command.cardNo(),
                    order.getFinalAmount()));
        });

        PaymentGatewayResponse pgResponse = paymentGateway.requestPayment(
                String.valueOf(command.memberId()),
                new PaymentGatewayRequest(
                        String.valueOf(payment.getOrderId()),
                        payment.getCardType().name(),
                        payment.getCardNo(),
                        payment.getAmount(),
                        null));

        Payment updated = transactionTemplate.execute(status -> {
            Payment p = paymentRepository.findById(payment.getId())
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                            PaymentExceptionMessage.Payment.NOT_FOUND.message()));

            if (pgResponse.success()) {
                p.assignTransactionKey(pgResponse.transactionKey());
            } else {
                p.fail(pgResponse.reason());
            }
            return p;
        });

        return PaymentInfo.from(updated);
    }

    @Transactional
    public void handleCallback(PaymentCallbackCommand command) {
        Payment payment = paymentRepository
                .findByTransactionKeyWithPessimisticLock(command.transactionKey())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        PaymentExceptionMessage.Payment.NOT_FOUND.message()));

        if (!payment.isPending()) {
            return;
        }

        if (command.isSuccess()) {
            payment.approve();
            Order order = orderRepository.findById(payment.getOrderId())
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                            OrderExceptionMessage.Order.NOT_FOUND.message()));
            order.pay();
        } else {
            payment.fail(command.reason());
        }
    }

    public void reconcile(Long paymentId) {
        Payment snapshot = transactionTemplate.execute(status -> {
            Payment p = paymentRepository.findById(paymentId)
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                            PaymentExceptionMessage.Payment.NOT_FOUND.message()));
            if (!p.isPending() || p.getTransactionKey() == null) {
                return null;
            }
            return p;
        });

        if (snapshot == null) {
            return;
        }

        PaymentGatewayStatusResponse pgStatus = paymentGateway.getPaymentStatus(
                String.valueOf(snapshot.getMemberId()), snapshot.getTransactionKey());

        if ("UNKNOWN".equalsIgnoreCase(pgStatus.status())) {
            log.warn("PG 상태 조회 불가 — paymentId={}", paymentId);
            return;
        }

        transactionTemplate.executeWithoutResult(status -> {
            Payment payment = paymentRepository
                    .findByTransactionKeyWithPessimisticLock(snapshot.getTransactionKey())
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                            PaymentExceptionMessage.Payment.NOT_FOUND.message()));

            if (!payment.isPending()) {
                return;
            }

            if ("SUCCESS".equalsIgnoreCase(pgStatus.status())) {
                payment.approve();
                Order order = orderRepository.findById(payment.getOrderId())
                        .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                                OrderExceptionMessage.Order.NOT_FOUND.message()));
                order.pay();
            } else if ("FAILED".equalsIgnoreCase(pgStatus.status())) {
                payment.fail(pgStatus.reason());
            }
        });
    }

    public void reconcileAll() {
        List<Payment> pendingPayments = paymentRepository.findByStatus(PaymentStatus.PENDING);
        for (Payment payment : pendingPayments) {
            try {
                reconcile(payment.getId());
            } catch (Exception e) {
                log.error("결제 복구 실패 — paymentId={}", payment.getId(), e);
            }
        }
    }

    @Transactional(readOnly = true)
    public PaymentInfo getByOrderId(Long orderId, Long memberId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        PaymentExceptionMessage.Payment.NOT_FOUND.message()));

        if (!payment.isOwnedBy(memberId)) {
            throw new CoreException(ErrorType.FORBIDDEN,
                    PaymentExceptionMessage.Payment.NOT_OWNER.message());
        }

        return PaymentInfo.from(payment);
    }
}
