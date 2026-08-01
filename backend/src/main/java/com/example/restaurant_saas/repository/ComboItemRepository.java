package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.ComboItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ComboItemRepository extends JpaRepository<ComboItem, UUID> {
    List<ComboItem> findByComboProductIdAndRestaurantIdOrderByCreatedAtAsc(UUID comboProductId, UUID restaurantId);
    void deleteByComboProductId(UUID comboProductId);
    boolean existsByComponentProductId(UUID componentProductId);
}
