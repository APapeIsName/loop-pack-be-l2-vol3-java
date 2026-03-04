package com.loopers.infrastructure.product;

import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.domain.catalog.product.ProductSortType;
import com.loopers.domain.catalog.product.QProduct;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;
    private final JPAQueryFactory queryFactory;

    private static final QProduct product = QProduct.product;

    @Override
    public Product save(Product product) {
        return productJpaRepository.save(product);
    }

    @Override
    public Optional<Product> findById(Long id) {
        return productJpaRepository.findById(id);
    }

    @Override
    public Optional<Product> findByIdWithPessimisticLock(Long id) {
        return productJpaRepository.findByIdWithPessimisticLock(id);
    }

    @Override
    public List<Product> findAllActive(ProductSortType sortType) {
        return queryFactory
                .selectFrom(product)
                .where(product.deletedAt.isNull())
                .orderBy(toOrderSpecifier(sortType))
                .fetch();
    }

    @Override
    public List<Product> findAll() {
        return productJpaRepository.findAll();
    }

    @Override
    public List<Product> findAllByIdIn(List<Long> ids) {
        return productJpaRepository.findAllByIdIn(ids);
    }

    @Override
    public void updateLikesCount(Long productId, int delta) {
        productJpaRepository.updateLikesCount(productId, delta);
    }

    @Override
    public void softDeleteByBrandId(Long brandId) {
        queryFactory
                .update(product)
                .set(product.deletedAt, ZonedDateTime.now())
                .where(
                        product.brandId.eq(brandId),
                        product.deletedAt.isNull()
                )
                .execute();
    }

    private OrderSpecifier<?> toOrderSpecifier(ProductSortType sortType) {
        return switch (sortType) {
            case LATEST -> product.createdAt.desc();
            case PRICE_ASC -> product.price.value.asc();
            case LIKES_DESC -> product.likesCount.desc();
        };
    }
}
