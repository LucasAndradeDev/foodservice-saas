package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.ProductModifierOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductModifierOptionRepository extends JpaRepository<ProductModifierOption, UUID> {
    List<ProductModifierOption> findByGroupIdOrderByCreatedAtAsc(UUID groupId);
    List<ProductModifierOption> findByGroupIdInOrderByCreatedAtAsc(List<UUID> groupIds);
    void deleteByGroupId(UUID groupId);
}
