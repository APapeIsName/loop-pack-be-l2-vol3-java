package com.loopers.domain.member.vo;

import com.loopers.domain.member.MemberExceptionMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class MemberNameTest {

    @Test
    void 유효한_이름으로_생성_성공() {
        // given
        String validName = "홍길동";

        // when

        // then
        assertDoesNotThrow(() -> MemberName.of(validName));
    }

    @Test
    void 이름은_2자_미만일_수_없음() {
        // given
        String shortName = "홍";

        // when

        // then
        assertThatThrownBy(() -> MemberName.of(shortName))
                .hasMessage(MemberExceptionMessage.Name.TOO_SHORT.message());
    }

    @Test
    void 이름은_40자를_초과할_수_없음() {
        // given
        String longName = "가".repeat(41);

        // when

        // then
        assertThatThrownBy(() -> MemberName.of(longName))
                .hasMessage(MemberExceptionMessage.Name.TOO_LONG.message());
    }

    @Test
    void 이름에_숫자가_포함될_수_없음() {
        // given
        String nameWithDigit = "홍길동1";

        // when

        // then
        assertThatThrownBy(() -> MemberName.of(nameWithDigit))
                .hasMessage(MemberExceptionMessage.Name.CONTAINS_INVALID_CHAR.message());
    }

    @Test
    void 이름에_특수문자가_포함될_수_없음() {
        // given
        String nameWithSpecial = "John@";

        // when

        // then
        assertThatThrownBy(() -> MemberName.of(nameWithSpecial))
                .hasMessage(MemberExceptionMessage.Name.CONTAINS_INVALID_CHAR.message());
    }
}
