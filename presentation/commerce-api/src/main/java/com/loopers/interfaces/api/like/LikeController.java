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

/**
 * 좋아요 API
 */
@RestController
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;
    private final MemberService memberService;

    /** 좋아요 등록 */
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

    /** 좋아요 취소 */
    @DeleteMapping("/api/products/{productId}/likes")
    public void unlike(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String password,
            @PathVariable Long productId
    ) {
        MemberInfo member = memberService.getMyInfo(loginId, password);
        likeService.unlike(member.memberId(), productId);
    }

    /** 내 좋아요 목록 조회 */
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
