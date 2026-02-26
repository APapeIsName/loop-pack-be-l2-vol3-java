package com.loopers.infrastructure.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.like.LikeSubjectType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class LikeRepositoryImpl implements LikeRepository {

    private final LikeJpaRepository likeJpaRepository;

    @Override
    public Like save(Like like) {
        return likeJpaRepository.save(like);
    }

    @Override
    public void delete(Like like) {
        likeJpaRepository.delete(like);
    }

    @Override
    public boolean existsByMemberIdAndSubjectTypeAndSubjectId(Long memberId, LikeSubjectType subjectType, Long subjectId) {
        return likeJpaRepository.existsByMemberIdAndSubjectTypeAndSubjectId(memberId, subjectType, subjectId);
    }

    @Override
    public Optional<Like> findByMemberIdAndSubjectTypeAndSubjectId(Long memberId, LikeSubjectType subjectType, Long subjectId) {
        return likeJpaRepository.findByMemberIdAndSubjectTypeAndSubjectId(memberId, subjectType, subjectId);
    }

    @Override
    public List<Like> findByMemberIdAndSubjectType(Long memberId, LikeSubjectType subjectType) {
        return likeJpaRepository.findByMemberIdAndSubjectType(memberId, subjectType);
    }
}
