package com.loopers.domain.like;

import com.loopers.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "likes")
public class Like extends BaseTimeEntity {

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false)
    private LikeSubjectType subjectType;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    private Like(Long memberId, LikeSubjectType subjectType, Long subjectId) {
        this.memberId = memberId;
        this.subjectType = subjectType;
        this.subjectId = subjectId;
    }

    public static Like mark(Long memberId, LikeSubjectType subjectType, Long subjectId) {
        return new Like(memberId, subjectType, subjectId);
    }

    public boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }

    public boolean isForSubject(LikeSubjectType subjectType, Long subjectId) {
        return this.subjectType == subjectType && this.subjectId.equals(subjectId);
    }
}
