package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {
    List<ProductImage> findByProductIdOrderBySortOrderAsc(UUID productId);
    List<ProductImage> findByProductIdInOrderBySortOrderAsc(List<UUID> productIds);
    void deleteByProductId(UUID productId);
}
