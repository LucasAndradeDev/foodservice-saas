package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.ComboSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ComboSlotRepository extends JpaRepository<ComboSlot, UUID> {
    List<ComboSlot> findByComboProductIdAndRestaurantIdOrderByCreatedAtAsc(UUID comboProductId, UUID restaurantId);
    void deleteByComboProductId(UUID comboProductId);
}
