package com.loopers.infrastructure.product;

import com.loopers.domain.catalog.product.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {

    List<Product> findAllByIdIn(List<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdWithPessimisticLock(@Param("id") Long id);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Product p SET p.likesCount = GREATEST(0, p.likesCount + :delta) WHERE p.id = :productId")
    void updateLikesCount(@Param("productId") Long productId, @Param("delta") int delta);
}
