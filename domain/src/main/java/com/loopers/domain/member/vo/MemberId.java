package com.loopers.domain.member.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.Objects;

public class MemberId {

    private final Long value;

    private MemberId(Long value) {
        this.value = value;
    }

    public static MemberId of(Long value) {
        if (value == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "MemberId는 null일 수 없습니다.");
        }
        return new MemberId(value);
    }

    public Long getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MemberId memberId)) return false;
        return Objects.equals(value, memberId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }
}
