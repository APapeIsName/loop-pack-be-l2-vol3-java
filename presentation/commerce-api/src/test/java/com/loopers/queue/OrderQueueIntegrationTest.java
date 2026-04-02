package com.loopers.queue;

import com.loopers.domain.queue.QueueProductRepository;
import com.loopers.domain.queue.WaitingQueueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrderQueueIntegrationTest {

    @Autowired
    private WaitingQueueRepository waitingQueueRepository;

    @Autowired
    private QueueProductRepository queueProductRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void 대기열_진입_시_순번이_0부터_시작한다() {
        // given & when
        long position = waitingQueueRepository.enqueue(1L, 100L, 10_000);

        // then
        assertThat(position).isEqualTo(0);
    }

    @Test
    void 대기열_진입_순서대로_순번이_증가한다() {
        // given
        waitingQueueRepository.enqueue(1L, 100L, 10_000);

        // when
        long position = waitingQueueRepository.enqueue(1L, 200L, 10_000);

        // then
        assertThat(position).isEqualTo(1);
    }

    @Test
    void 같은_유저가_중복_진입하면_기존_순번을_반환한다() {
        // given
        waitingQueueRepository.enqueue(1L, 100L, 10_000);

        // when
        long position = waitingQueueRepository.enqueue(1L, 100L, 10_000);

        // then
        assertThat(position).isEqualTo(0);
    }

    @Test
    void 대기열_용량_초과_시_마이너스1을_반환한다() {
        // given
        waitingQueueRepository.enqueue(1L, 100L, 1);

        // when
        long position = waitingQueueRepository.enqueue(1L, 200L, 1);

        // then
        assertThat(position).isEqualTo(-1);
    }

    @Test
    void 순번_조회_시_대기열에_있으면_순번을_반환한다() {
        // given
        waitingQueueRepository.enqueue(1L, 100L, 10_000);
        waitingQueueRepository.enqueue(1L, 200L, 10_000);

        // when
        Long position = waitingQueueRepository.getPosition(1L, 200L);

        // then
        assertThat(position).isEqualTo(1);
    }

    @Test
    void 순번_조회_시_대기열에_없으면_null을_반환한다() {
        // when
        Long position = waitingQueueRepository.getPosition(1L, 999L);

        // then
        assertThat(position).isNull();
    }

    @Test
    void 전체_대기_인원을_조회한다() {
        // given
        waitingQueueRepository.enqueue(1L, 100L, 10_000);
        waitingQueueRepository.enqueue(1L, 200L, 10_000);
        waitingQueueRepository.enqueue(1L, 300L, 10_000);

        // when
        long count = waitingQueueRepository.getTotalCount(1L);

        // then
        assertThat(count).isEqualTo(3);
    }

    @Test
    void 앞에서부터_N명을_꺼낸다() {
        // given
        waitingQueueRepository.enqueue(1L, 100L, 10_000);
        waitingQueueRepository.enqueue(1L, 200L, 10_000);
        waitingQueueRepository.enqueue(1L, 300L, 10_000);

        // when
        List<Long> popped = waitingQueueRepository.popFront(1L, 2);

        // then
        assertThat(popped).containsExactly(100L, 200L);
    }

    @Test
    void 꺼낸_후_대기열에서_제거된다() {
        // given
        waitingQueueRepository.enqueue(1L, 100L, 10_000);
        waitingQueueRepository.enqueue(1L, 200L, 10_000);
        waitingQueueRepository.popFront(1L, 1);

        // when
        long count = waitingQueueRepository.getTotalCount(1L);

        // then
        assertThat(count).isEqualTo(1);
    }

    @Test
    void 자발적_이탈_시_대기열에서_제거된다() {
        // given
        waitingQueueRepository.enqueue(1L, 100L, 10_000);

        // when
        boolean removed = waitingQueueRepository.dequeue(1L, 100L);

        // then
        assertThat(removed).isTrue();
    }

    @Test
    void 토큰_발급_후_존재_여부를_확인한다() {
        // given
        waitingQueueRepository.issueToken(1L, 100L, "token-abc", 300);

        // when
        boolean hasToken = waitingQueueRepository.hasToken(1L, 100L);

        // then
        assertThat(hasToken).isTrue();
    }

    @Test
    void 토큰_검증_성공_시_토큰이_삭제된다() {
        // given
        waitingQueueRepository.issueToken(1L, 100L, "token-abc", 300);

        // when
        boolean consumed = waitingQueueRepository.validateAndConsumeToken(1L, 100L, "token-abc");

        // then
        assertThat(consumed).isTrue();
    }

    @Test
    void 토큰_소비_후_더이상_존재하지_않는다() {
        // given
        waitingQueueRepository.issueToken(1L, 100L, "token-abc", 300);
        waitingQueueRepository.validateAndConsumeToken(1L, 100L, "token-abc");

        // when
        boolean hasToken = waitingQueueRepository.hasToken(1L, 100L);

        // then
        assertThat(hasToken).isFalse();
    }

    @Test
    void 잘못된_토큰으로_검증하면_실패한다() {
        // given
        waitingQueueRepository.issueToken(1L, 100L, "token-abc", 300);

        // when
        boolean consumed = waitingQueueRepository.validateAndConsumeToken(1L, 100L, "wrong-token");

        // then
        assertThat(consumed).isFalse();
    }

    @Test
    void 활성_상품_등록_후_조회할_수_있다() {
        // given
        queueProductRepository.registerActiveProduct(1L);

        // when
        boolean active = queueProductRepository.isActiveProduct(1L);

        // then
        assertThat(active).isTrue();
    }

    @Test
    void 활성_상품_해제_후_비활성_상태가_된다() {
        // given
        queueProductRepository.registerActiveProduct(1L);
        queueProductRepository.unregisterActiveProduct(1L);

        // when
        boolean active = queueProductRepository.isActiveProduct(1L);

        // then
        assertThat(active).isFalse();
    }

    @Test
    void 활성_상품_목록을_전체_조회한다() {
        // given
        queueProductRepository.registerActiveProduct(1L);
        queueProductRepository.registerActiveProduct(2L);

        // when
        Set<Long> activeIds = queueProductRepository.getActiveProductIds();

        // then
        assertThat(activeIds).containsExactlyInAnyOrder(1L, 2L);
    }

}
