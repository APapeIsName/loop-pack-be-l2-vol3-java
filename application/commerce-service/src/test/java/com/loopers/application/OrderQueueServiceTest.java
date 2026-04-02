package com.loopers.application;

import com.loopers.application.service.OrderQueueService;
import com.loopers.application.service.OrderService;
import com.loopers.application.service.dto.QueueEnterCommand;
import com.loopers.application.service.dto.QueuePositionInfo;
import com.loopers.domain.queue.QueueProductRepository;
import com.loopers.domain.queue.WaitingQueueRepository;
import com.loopers.domain.queue.QueueExceptionMessage;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderQueueServiceTest {

    private OrderQueueService orderQueueService;

    @Mock
    private WaitingQueueRepository waitingQueueRepository;

    @Mock
    private QueueProductRepository queueProductRepository;

    @Mock
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderQueueService = new OrderQueueService(
                10_000L, 300L, 10, 100, 80,
                waitingQueueRepository, queueProductRepository, orderService
        );
    }

    @Test
    void 대기열_진입_성공_시_순번을_반환한다() {
        // given
        QueueEnterCommand command = new QueueEnterCommand(1L, 42L);
        given(queueProductRepository.isActiveProduct(1L)).willReturn(true);
        given(waitingQueueRepository.enqueue(eq(1L), eq(42L), anyLong())).willReturn(5L);
        given(waitingQueueRepository.getTotalCount(1L)).willReturn(100L);

        // when
        QueuePositionInfo result = orderQueueService.enterQueue(command);

        // then
        assertThat(result.position()).isEqualTo(5L);
    }

    @Test
    void 대기열_진입_시_비활성_상품이면_예외() {
        // given
        QueueEnterCommand command = new QueueEnterCommand(1L, 42L);
        given(queueProductRepository.isActiveProduct(1L)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> orderQueueService.enterQueue(command))
                .isInstanceOf(CoreException.class)
                .hasMessage(QueueExceptionMessage.Queue.QUEUE_NOT_ACTIVE.message());
    }

    @Test
    void 대기열_진입_시_가득_차면_예외() {
        // given
        QueueEnterCommand command = new QueueEnterCommand(1L, 42L);
        given(queueProductRepository.isActiveProduct(1L)).willReturn(true);
        given(waitingQueueRepository.enqueue(eq(1L), eq(42L), anyLong())).willReturn(-1L);

        // when & then
        assertThatThrownBy(() -> orderQueueService.enterQueue(command))
                .isInstanceOf(CoreException.class)
                .hasMessage(QueueExceptionMessage.Queue.QUEUE_FULL.message());
    }

    @Test
    void 순번_조회_시_대기중이면_순번과_예상시간을_반환한다() {
        // given
        given(waitingQueueRepository.hasToken(1L, 42L)).willReturn(false);
        given(waitingQueueRepository.getPosition(1L, 42L)).willReturn(50L);
        given(waitingQueueRepository.getTotalCount(1L)).willReturn(200L);

        // when
        QueuePositionInfo result = orderQueueService.getPosition(1L, 42L);

        // then
        assertThat(result.position()).isEqualTo(50L);
    }

    @Test
    void 순번_조회_시_토큰이_발급되었으면_토큰을_반환한다() {
        // given
        given(waitingQueueRepository.hasToken(1L, 42L)).willReturn(true);
        given(waitingQueueRepository.getToken(1L, 42L)).willReturn("abc-123");

        // when
        QueuePositionInfo result = orderQueueService.getPosition(1L, 42L);

        // then
        assertThat(result.hasToken()).isTrue();
    }

    @Test
    void 순번_조회_시_토큰이_발급되었으면_토큰값을_포함한다() {
        // given
        given(waitingQueueRepository.hasToken(1L, 42L)).willReturn(true);
        given(waitingQueueRepository.getToken(1L, 42L)).willReturn("abc-123");

        // when
        QueuePositionInfo result = orderQueueService.getPosition(1L, 42L);

        // then
        assertThat(result.token()).isEqualTo("abc-123");
    }

    @Test
    void 순번_조회_시_대기열에_없으면_예외() {
        // given
        given(waitingQueueRepository.hasToken(1L, 42L)).willReturn(false);
        given(waitingQueueRepository.getPosition(1L, 42L)).willReturn(null);

        // when & then
        assertThatThrownBy(() -> orderQueueService.getPosition(1L, 42L))
                .isInstanceOf(CoreException.class)
                .hasMessage(QueueExceptionMessage.Queue.NOT_IN_QUEUE.message());
    }

    @Test
    void 대기열_이탈_시_대기열에서_제거된다() {
        // given & when
        orderQueueService.exitQueue(1L, 42L);

        // then
        verify(waitingQueueRepository).dequeue(1L, 42L);
    }

    @Test
    void 토큰_검증_성공_시_토큰이_소비된다() {
        // given
        given(waitingQueueRepository.validateAndConsumeToken(1L, 42L, "abc-123")).willReturn(true);

        // when
        orderQueueService.validateTokenForOrder(1L, 42L, "abc-123");

        // then
        verify(waitingQueueRepository).validateAndConsumeToken(1L, 42L, "abc-123");
    }

    @Test
    void 토큰_검증_시_토큰이_없으면_예외() {
        // when & then
        assertThatThrownBy(() -> orderQueueService.validateTokenForOrder(1L, 42L, null))
                .isInstanceOf(CoreException.class)
                .hasMessage(QueueExceptionMessage.Token.NO_TOKEN.message());
    }

    @Test
    void 토큰_검증_시_유효하지_않은_토큰이면_예외() {
        // given
        given(waitingQueueRepository.validateAndConsumeToken(1L, 42L, "wrong-token")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> orderQueueService.validateTokenForOrder(1L, 42L, "wrong-token"))
                .isInstanceOf(CoreException.class)
                .hasMessage(QueueExceptionMessage.Token.INVALID_TOKEN.message());
    }

    @Test
    void 대기열_활성화_시_활성_상품으로_등록된다() {
        // given & when
        orderQueueService.activateQueue(1L);

        // then
        verify(queueProductRepository).registerActiveProduct(1L);
    }

    @Test
    void 대기열_비활성화_시_활성_상품에서_제거된다() {
        // given & when
        orderQueueService.deactivateQueue(1L);

        // then
        verify(queueProductRepository).unregisterActiveProduct(1L);
    }
}
