package com.loopers.infrastructure.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeSubjectType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LikeJpaRepository extends JpaRepository<Like, Long> {

    boolean existsByMemberIdAndSubjectTypeAndSubjectId(Long memberId, LikeSubjectType subjectType, Long subjectId);

    Optional<Like> findByMemberIdAndSubjectTypeAndSubjectId(Long memberId, LikeSubjectType subjectType, Long subjectId);

    List<Like> findByMemberIdAndSubjectType(Long memberId, LikeSubjectType subjectType);
}
