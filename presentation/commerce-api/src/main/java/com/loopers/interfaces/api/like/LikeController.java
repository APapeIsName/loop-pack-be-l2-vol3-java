package com.loopers.interfaces.api.like;

import com.loopers.application.service.LikeService;
import com.loopers.application.service.MemberService;
import com.loopers.application.service.dto.LikeRegisterCommand;
import com.loopers.application.service.dto.MemberInfo;
import com.loopers.interfaces.api.product.dto.ProductApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;
    private final MemberService memberService;

    @PostMapping("/api/products/{productId}/likes")
    @ResponseStatus(HttpStatus.CREATED)
    public void like(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String password,
            @PathVariable Long productId
    ) {
        MemberInfo member = memberService.getMyInfo(loginId, password);
        likeService.like(new LikeRegisterCommand(member.memberId(), productId));
    }

    @DeleteMapping("/api/products/{productId}/likes")
    public void unlike(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String password,
            @PathVariable Long productId
    ) {
        MemberInfo member = memberService.getMyInfo(loginId, password);
        likeService.unlike(member.memberId(), productId);
    }

    @GetMapping("/api/likes")
    public List<ProductApiResponse> getMyLikes(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String password
    ) {
        MemberInfo member = memberService.getMyInfo(loginId, password);
        return likeService.getMyLikes(member.memberId()).stream()
                .map(ProductApiResponse::from)
                .toList();
    }
}
