package com.loopers.infrastructure.product;

import com.loopers.domain.catalog.product.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {
}
