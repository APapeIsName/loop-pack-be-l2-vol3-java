package com.loopers.interfaces.api.queue;

import com.loopers.application.service.MemberService;
import com.loopers.application.service.OrderQueueService;
import com.loopers.application.service.dto.MemberInfo;
import com.loopers.application.service.dto.QueueEnterCommand;
import com.loopers.application.service.dto.QueuePositionInfo;
import com.loopers.interfaces.api.queue.dto.QueuePositionApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/queue/products")
@RequiredArgsConstructor
public class OrderQueueController {

    private static final long SSE_TIMEOUT = 30_000L;
    private static final long SSE_POLL_INTERVAL = 2_000L;
    private static final long SSE_POSITION_THRESHOLD = 100;

    private final OrderQueueService orderQueueService;
    private final MemberService memberService;

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService sseScheduler = Executors.newScheduledThreadPool(4);

    @PostMapping("/{productId}/enter")
    @ResponseStatus(HttpStatus.CREATED)
    public QueuePositionApiResponse enter(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String password,
            @PathVariable Long productId
    ) {
        MemberInfo member = memberService.getMyInfo(loginId, password);
        return QueuePositionApiResponse.from(
                orderQueueService.enterQueue(new QueueEnterCommand(productId, member.memberId())));
    }

    @GetMapping("/{productId}/position")
    public QueuePositionApiResponse getPosition(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String password,
            @PathVariable Long productId
    ) {
        MemberInfo member = memberService.getMyInfo(loginId, password);
        return QueuePositionApiResponse.from(
                orderQueueService.getPosition(productId, member.memberId()));
    }

    @GetMapping(value = "/{productId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String password,
            @PathVariable Long productId
    ) {
        MemberInfo member = memberService.getMyInfo(loginId, password);
        Long memberId = member.memberId();
        String emitterKey = productId + ":" + memberId;

        QueuePositionInfo currentPosition = orderQueueService.getPosition(productId, memberId);
        if (!currentPosition.hasToken() && currentPosition.position() > SSE_POSITION_THRESHOLD) {
            SseEmitter rejected = new SseEmitter(0L);
            try {
                rejected.send(SseEmitter.event()
                        .name("fallback")
                        .data(Map.of("message", "순번이 100번 이후입니다. Polling을 사용해주세요.",
                                "position", currentPosition.position())));
                rejected.complete();
            } catch (IOException ignored) {
            }
            return rejected;
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        emitter.onCompletion(() -> emitters.remove(emitterKey));
        emitter.onTimeout(() -> emitters.remove(emitterKey));
        emitter.onError(e -> emitters.remove(emitterKey));

        emitters.put(emitterKey, emitter);

        sseScheduler.scheduleAtFixedRate(() -> {
            SseEmitter current = emitters.get(emitterKey);
            if (current == null) {
                return;
            }
            try {
                QueuePositionInfo info = orderQueueService.getPosition(productId, memberId);
                current.send(SseEmitter.event()
                        .name("position")
                        .data(QueuePositionApiResponse.from(info)));

                if (info.hasToken()) {
                    current.send(SseEmitter.event()
                            .name("token")
                            .data(Map.of("token", info.token())));
                    current.complete();
                    emitters.remove(emitterKey);
                }
            } catch (IOException e) {
                emitters.remove(emitterKey);
            } catch (Exception e) {
                try {
                    current.send(SseEmitter.event()
                            .name("closed")
                            .data(Map.of("message", "대기열에서 이탈되었습니다.")));
                    current.complete();
                } catch (IOException ignored) {
                }
                emitters.remove(emitterKey);
            }
        }, 0, SSE_POLL_INTERVAL, TimeUnit.MILLISECONDS);

        return emitter;
    }

    @DeleteMapping("/{productId}/exit")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void exit(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String password,
            @PathVariable Long productId
    ) {
        MemberInfo member = memberService.getMyInfo(loginId, password);
        orderQueueService.exitQueue(productId, member.memberId());
    }
}
