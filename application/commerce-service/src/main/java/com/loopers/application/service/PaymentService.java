package com.loopers.application.service;

import com.loopers.application.service.dto.PaymentCallbackCommand;
import com.loopers.application.service.dto.PaymentInfo;
import com.loopers.application.service.dto.PaymentRequestCommand;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderExceptionMessage;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.payment.*;
import com.loopers.domain.payment.event.PaymentApprovedEvent;
import com.loopers.domain.payment.event.PaymentTerminallyFailedEvent;
import com.loopers.domain.payment.gateway.PaymentGatewayRequest;
import com.loopers.domain.payment.gateway.PaymentGatewayResponse;
import com.loopers.domain.payment.gateway.PaymentGatewayStatusResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentGateway paymentGateway;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentInfo requestPayment(PaymentRequestCommand command) {
        Payment saved = transactionTemplate.execute(status -> {
            Order order = orderRepository.findByIdWithPessimisticLock(command.orderId())
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                            OrderExceptionMessage.Order.NOT_FOUND.message()));

            if (!order.isOwnedBy(command.memberId())) {
                throw new CoreException(ErrorType.FORBIDDEN,
                        OrderExceptionMessage.Order.NOT_OWNER.message());
            }
            if (!order.isAccepted()) {
                throw new CoreException(ErrorType.CONFLICT,
                        OrderExceptionMessage.Order.NOT_ACCEPTED.message());
            }

            paymentRepository.findByOrderIdAndStatusIn(command.orderId(),
                            List.of(PaymentStatus.REQUESTED, PaymentStatus.PENDING))
                    .ifPresent(it -> {
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
                        String.valueOf(saved.getOrderId()),
                        saved.getCardType().name(),
                        saved.getCardNo(),
                        saved.getAmount().getValue(),
                        null));

        Payment updated = transactionTemplate.execute(status -> {
            Payment target = paymentRepository.findById(saved.getId())
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                            PaymentExceptionMessage.Payment.NOT_FOUND.message()));

            if (pgResponse.success()) {
                target.pend(pgResponse.transactionKey());
            } else {
                target.fail(pgResponse.reason());
            }
            return target;
        });

        return PaymentInfo.from(updated);
    }

    @Transactional
    public void handleCallback(PaymentCallbackCommand command) {
        Payment target = paymentRepository
                .findByTransactionKeyWithPessimisticLock(command.transactionKey())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        PaymentExceptionMessage.Payment.NOT_FOUND.message()));

        if (target.isCompleted()) {
            return;
        }

        if (command.isSuccess()) {
            target.approve();
            eventPublisher.publishEvent(PaymentApprovedEvent.of(
                    target.getId(), target.getOrderId(), target.getMemberId(), target.getAmount().getValue()));
        } else {
            target.fail(command.reason());
        }
    }

    public void reconcile(Long paymentId) {

        Payment payment = transactionTemplate.execute(status -> {
            Payment found = paymentRepository.findById(paymentId)
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                            PaymentExceptionMessage.Payment.NOT_FOUND.message()));
            if (found.isCompleted() || !found.hasTransactionKey()) {
                return null;
            }
            return found;
        });

        if (payment == null) {
            return;
        }

        PaymentGatewayStatusResponse pgStatus = paymentGateway.getPaymentStatus(
                String.valueOf(payment.getMemberId()), payment.getTransactionKey());

        if (pgStatus.isUnknown()) {
            log.warn("PG 상태 조회 불가 — paymentId={}", paymentId);
            return;
        }

        transactionTemplate.executeWithoutResult(status -> {
            Payment target = paymentRepository
                    .findByTransactionKeyWithPessimisticLock(payment.getTransactionKey())
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                            PaymentExceptionMessage.Payment.NOT_FOUND.message()));

            if (target.isCompleted()) {
                return;
            }

            if (pgStatus.isSuccess()) {
                target.approve();
                eventPublisher.publishEvent(PaymentApprovedEvent.of(
                        target.getId(), target.getOrderId(), target.getMemberId(), target.getAmount().getValue()));
            } else if (pgStatus.isFailed()) {
                target.fail(pgStatus.reason());
                eventPublisher.publishEvent(PaymentTerminallyFailedEvent.of(
                        target.getId(), target.getOrderId(), target.getMemberId(), target.getAmount().getValue(), pgStatus.reason()));
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

    public void expireAbandonedPayments(ZonedDateTime threshold) {
        List<Payment> requestedPayments = paymentRepository.findByStatus(PaymentStatus.REQUESTED);

        List<Payment> abandoned = requestedPayments.stream()
                .filter(it -> it.isCreatedBefore(threshold))
                .toList();

        for (Payment payment : abandoned) {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    Payment target = paymentRepository.findById(payment.getId())
                            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                                    PaymentExceptionMessage.Payment.NOT_FOUND.message()));

                    if (target.isRequested()) {
                        target.fail("결제 처리 시간 초과 — PG 응답 미수신");
                        eventPublisher.publishEvent(PaymentTerminallyFailedEvent.of(
                                target.getId(), target.getOrderId(), target.getMemberId(), target.getAmount().getValue(), "결제 처리 시간 초과 — PG 응답 미수신"));
                        log.info("방치된 REQUESTED 결제 FAILED 처리 — paymentId={}", target.getId());
                    }
                });
            } catch (Exception e) {
                log.error("방치된 REQUESTED 결제 실패 처리 오류 — paymentId={}", payment.getId(), e);
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
