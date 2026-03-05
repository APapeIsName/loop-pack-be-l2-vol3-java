package com.loopers.domain.like;

public class LikeFixture {

    public static final Long DEFAULT_MEMBER_ID = 1L;
    public static final Long DEFAULT_SUBJECT_ID = 100L;

    public static Like create() {
        return Like.mark(DEFAULT_MEMBER_ID, LikeSubjectType.PRODUCT, DEFAULT_SUBJECT_ID);
    }

    public static Like create(Long memberId, Long subjectId) {
        return Like.mark(memberId, LikeSubjectType.PRODUCT, subjectId);
    }
}
