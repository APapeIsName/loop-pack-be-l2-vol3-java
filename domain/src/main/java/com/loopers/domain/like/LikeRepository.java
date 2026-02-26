package com.loopers.domain.like;

import java.util.Optional;

public interface LikeRepository {

    Like save(Like like);

    void delete(Like like);

    boolean existsByMemberIdAndSubjectTypeAndSubjectId(Long memberId, LikeSubjectType subjectType, Long subjectId);

    Optional<Like> findByMemberIdAndSubjectTypeAndSubjectId(Long memberId, LikeSubjectType subjectType, Long subjectId);

    java.util.List<Like> findByMemberIdAndSubjectType(Long memberId, LikeSubjectType subjectType);
}
