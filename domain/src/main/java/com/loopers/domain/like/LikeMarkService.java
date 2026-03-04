package com.loopers.domain.like;

import com.loopers.domain.catalog.ActiveProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LikeMarkService {

    private final LikeRepository likeRepository;
    private final ActiveProductService activeProductService;

    public Like mark(Long memberId, Long productId) {
        activeProductService.get(productId);

        if (likeRepository.existsByMemberIdAndSubjectTypeAndSubjectId(
                memberId, LikeSubjectType.PRODUCT, productId)) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    LikeExceptionMessage.Like.ALREADY_LIKED.message());
        }

        return likeRepository.save(Like.mark(memberId, LikeSubjectType.PRODUCT, productId));
    }

    public void unmark(Long memberId, Long productId) {
        Like like = likeRepository.findByMemberIdAndSubjectTypeAndSubjectId(
                        memberId, LikeSubjectType.PRODUCT, productId)
                .orElseThrow(() -> new CoreException(ErrorType.BAD_REQUEST,
                        LikeExceptionMessage.Like.NOT_LIKED.message()));

        likeRepository.delete(like);
    }
}
