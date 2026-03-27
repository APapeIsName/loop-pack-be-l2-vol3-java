package com.loopers.event;

import com.loopers.application.service.LikeService;
import com.loopers.application.service.dto.LikeRegisterCommand;
import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.domain.common.vo.Money;
import com.loopers.domain.catalog.product.vo.Stock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EventTransactionTest {

    @Autowired
    private LikeService likeService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DELETE FROM likes");
        jdbcTemplate.execute("DELETE FROM product");
        jdbcTemplate.execute("DELETE FROM brand");
    }

    @Test
    void 좋아요_등록_성공_시_like_레코드가_저장된다() {
        // given
        Brand brand = brandRepository.save(Brand.register("브랜드"));
        Product product = productRepository.save(
                Product.register("상품", "설명", Money.of(10000), Stock.of(100), brand.getId()));

        // when
        likeService.like(new LikeRegisterCommand(1L, product.getId()));

        // then
        Long likeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM likes WHERE subject_id = ?", Long.class, product.getId());
        assertThat(likeCount).isEqualTo(1L);
    }
}
