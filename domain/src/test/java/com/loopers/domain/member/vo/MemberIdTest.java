package com.loopers.domain.member.vo;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberIdTest {

    @Test
    void null_값으로_생성_시_예외() {
        // given
        Long value = null;

        // when & then
        assertThatThrownBy(() -> MemberId.of(value))
                .isInstanceOf(CoreException.class);
    }

    @Test
    void 같은_값이면_동등하다() {
        // given
        MemberId id1 = MemberId.of(1L);
        MemberId id2 = MemberId.of(1L);

        // when & then
        assertThat(id1).isEqualTo(id2);
    }

    @Test
    void 다른_값이면_동등하지_않다() {
        // given
        MemberId id1 = MemberId.of(1L);
        MemberId id2 = MemberId.of(2L);

        // when & then
        assertThat(id1).isNotEqualTo(id2);
    }
}
