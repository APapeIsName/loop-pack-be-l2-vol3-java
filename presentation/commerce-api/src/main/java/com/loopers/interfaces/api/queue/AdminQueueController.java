package com.loopers.interfaces.api.queue;

import com.loopers.application.service.OrderQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/queue/products")
@RequiredArgsConstructor
public class AdminQueueController {

    private final OrderQueueService orderQueueService;

    @PostMapping("/{productId}/activate")
    @ResponseStatus(HttpStatus.CREATED)
    public void activate(@PathVariable Long productId) {
        orderQueueService.activateQueue(productId);
    }

    @DeleteMapping("/{productId}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long productId) {
        orderQueueService.deactivateQueue(productId);
    }
}
