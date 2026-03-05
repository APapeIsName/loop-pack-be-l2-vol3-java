package com.loopers.domain.like;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LikeTest {

    @Test
    void 본인_확인_성공() {
        // given
        Like like = Like.mark(1L, LikeSubjectType.PRODUCT, 100L);

        // when & then
        assertThat(like.isOwnedBy(1L)).isTrue();
    }

    @Test
    void 본인_아니면_false() {
        // given
        Like like = Like.mark(1L, LikeSubjectType.PRODUCT, 100L);

        // when & then
        assertThat(like.isOwnedBy(99L)).isFalse();
    }

    @Test
    void 대상_확인_성공() {
        // given
        Like like = Like.mark(1L, LikeSubjectType.PRODUCT, 100L);

        // when & then
        assertThat(like.isForSubject(LikeSubjectType.PRODUCT, 100L)).isTrue();
    }

    @Test
    void 대상_아니면_false() {
        // given
        Like like = Like.mark(1L, LikeSubjectType.PRODUCT, 100L);

        // when & then
        assertThat(like.isForSubject(LikeSubjectType.PRODUCT, 999L)).isFalse();
    }
}
