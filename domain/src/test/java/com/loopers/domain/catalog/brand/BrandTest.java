package com.loopers.domain.catalog.brand;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrandTest {

    @Test
    void 브랜드_등록_성공() {
        // given
        String name = "나이키";

        // when
        Brand brand = Brand.register(name);

        // then
        assertThat(brand.hasName(name)).isTrue();
    }

    @Test
    void 이름_1자_등록_성공() {
        // when
        Brand brand = Brand.register("A");

        // then
        assertThat(brand.hasName("A")).isTrue();
    }

    @Test
    void 이름_100자_경계값_등록_성공() {
        // given
        String name = "a".repeat(100);

        // when
        Brand brand = Brand.register(name);

        // then
        assertThat(brand.hasName(name)).isTrue();
    }

    @Test
    void 빈_이름으로_등록_시_예외() {
        // when & then
        assertThatThrownBy(() -> Brand.register(""))
                .isInstanceOf(CoreException.class)
                .hasMessage("이름은 1자 이상 100자 이하여야 합니다.");
    }

    @Test
    void null_이름으로_등록_시_예외() {
        // when & then
        assertThatThrownBy(() -> Brand.register(null))
                .isInstanceOf(CoreException.class)
                .hasMessage("이름은 1자 이상 100자 이하여야 합니다.");
    }

    @Test
    void 공백만_있는_이름_등록_시_예외() {
        // when & then
        assertThatThrownBy(() -> Brand.register("   "))
                .isInstanceOf(CoreException.class)
                .hasMessage("이름은 1자 이상 100자 이하여야 합니다.");
    }

    @Test
    void 이름_길이_초과_시_예외() {
        // given
        String longName = "a".repeat(101);

        // when & then
        assertThatThrownBy(() -> Brand.register(longName))
                .isInstanceOf(CoreException.class)
                .hasMessage("이름은 1자 이상 100자 이하여야 합니다.");
    }

    @Test
    void 이름_수정_성공() {
        // given
        Brand brand = Brand.register("나이키");

        // when
        brand.updateName("아디다스");

        // then
        assertThat(brand.hasName("아디다스")).isTrue();
    }

    @Test
    void 수정_시_빈_이름_예외() {
        // given
        Brand brand = Brand.register("나이키");

        // when & then
        assertThatThrownBy(() -> brand.updateName(""))
                .isInstanceOf(CoreException.class)
                .hasMessage("이름은 1자 이상 100자 이하여야 합니다.");
    }

    @Test
    void 수정_시_이름_길이_초과_예외() {
        // given
        Brand brand = Brand.register("나이키");

        // when & then
        assertThatThrownBy(() -> brand.updateName("a".repeat(101)))
                .isInstanceOf(CoreException.class)
                .hasMessage("이름은 1자 이상 100자 이하여야 합니다.");
    }

    @Test
    void 삭제_시_deletedAt_설정() {
        // given
        Brand brand = Brand.register("나이키");

        // when
        brand.delete();

        // then
        assertThat(brand.isDeleted()).isTrue();
    }

    @Test
    void 삭제_시_이름에_deleted_접미사_추가() {
        // given
        Brand brand = Brand.register("나이키");

        // when
        brand.delete();

        // then
        assertThat(brand.hasNameStartingWith("나이키_deleted_")).isTrue();
    }

    @Test
    void 이미_삭제된_브랜드_재삭제_시_예외() {
        // given
        Brand brand = Brand.register("나이키");
        brand.delete();

        // when & then
        assertThatThrownBy(brand::delete)
                .isInstanceOf(CoreException.class)
                .hasMessage(BrandExceptionMessage.Brand.ALREADY_DELETED.message());
    }

    @Test
    void 이미_삭제된_브랜드_수정_시_예외() {
        // given
        Brand brand = Brand.register("나이키");
        brand.delete();

        // when & then
        assertThatThrownBy(() -> brand.updateName("아디다스"))
                .isInstanceOf(CoreException.class)
                .hasMessage(BrandExceptionMessage.Brand.ALREADY_DELETED.message());
    }
}
