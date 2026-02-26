package com.loopers.application;

import com.loopers.application.service.BrandService;
import com.loopers.application.service.dto.BrandCreateCommand;
import com.loopers.application.service.dto.BrandInfo;
import com.loopers.application.service.dto.BrandUpdateCommand;
import com.loopers.domain.catalog.BrandDeleteService;
import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandExceptionMessage;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BrandServiceTest {

    @InjectMocks
    private BrandService brandService;

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private BrandDeleteService brandDeleteService;

    @Test
    void 브랜드_생성_성공_시_저장된다() {
        // given
        BrandCreateCommand command = new BrandCreateCommand("나이키");
        given(brandRepository.existsByName("나이키")).willReturn(false);

        // when
        brandService.create(command);

        // then
        verify(brandRepository).save(any(Brand.class));
    }

    @Test
    void 브랜드_생성_시_중복_이름이면_예외() {
        // given
        BrandCreateCommand command = new BrandCreateCommand("나이키");
        given(brandRepository.existsByName("나이키")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> brandService.create(command))
                .isInstanceOf(CoreException.class)
                .hasMessage(BrandExceptionMessage.Brand.DUPLICATE_NAME.message());
    }

    @Test
    void 브랜드_단건_조회_성공() {
        // given
        Long brandId = 1L;
        Brand brand = Brand.register("나이키");
        given(brandRepository.findById(brandId)).willReturn(Optional.of(brand));

        // when
        BrandInfo result = brandService.getById(brandId);

        // then
        assertThat(result.name()).isEqualTo("나이키");
    }

    @Test
    void 브랜드_단건_조회_시_존재하지_않으면_예외() {
        // given
        Long brandId = 999L;
        given(brandRepository.findById(brandId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> brandService.getById(brandId))
                .isInstanceOf(CoreException.class)
                .hasMessage(BrandExceptionMessage.Brand.NOT_FOUND.message());
    }

    @Test
    void 브랜드_전체_목록_조회() {
        // given
        List<Brand> brands = List.of(Brand.register("나이키"), Brand.register("아디다스"));
        given(brandRepository.findAll()).willReturn(brands);

        // when
        List<BrandInfo> result = brandService.getAll();

        // then
        assertThat(result).hasSize(2);
    }

    @Test
    void 활성_브랜드_목록_조회() {
        // given
        List<Brand> brands = List.of(Brand.register("나이키"), Brand.register("아디다스"));
        given(brandRepository.findAllByDeletedAtIsNull()).willReturn(brands);

        // when
        List<BrandInfo> result = brandService.getActiveBrands();

        // then
        assertThat(result).hasSize(2);
    }

    @Test
    void 브랜드_수정_시_존재하지_않으면_예외() {
        // given
        Long brandId = 999L;
        BrandUpdateCommand command = new BrandUpdateCommand("아디다스");
        given(brandRepository.findById(brandId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> brandService.update(brandId, command))
                .isInstanceOf(CoreException.class)
                .hasMessage(BrandExceptionMessage.Brand.NOT_FOUND.message());
    }

    @Test
    void 브랜드_수정_시_다른_브랜드와_이름_중복이면_예외() {
        // given
        Long brandId = 1L;
        Brand brand = Brand.register("나이키");
        BrandUpdateCommand command = new BrandUpdateCommand("아디다스");
        given(brandRepository.findById(brandId)).willReturn(Optional.of(brand));
        given(brandRepository.existsByName("아디다스")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> brandService.update(brandId, command))
                .isInstanceOf(CoreException.class)
                .hasMessage(BrandExceptionMessage.Brand.DUPLICATE_NAME.message());
    }

    @Test
    void 브랜드_수정_성공() {
        // given
        Long brandId = 1L;
        Brand brand = Brand.register("나이키");
        BrandUpdateCommand command = new BrandUpdateCommand("아디다스");
        given(brandRepository.findById(brandId)).willReturn(Optional.of(brand));
        given(brandRepository.existsByName("아디다스")).willReturn(false);

        // when
        brandService.update(brandId, command);

        // then
        assertThat(brand.hasName("아디다스")).isTrue();
    }

    @Test
    void 브랜드_삭제_시_BrandDeleteService에_위임() {
        // given
        Long brandId = 1L;

        // when
        brandService.delete(brandId);

        // then
        verify(brandDeleteService).delete(brandId);
    }
}
