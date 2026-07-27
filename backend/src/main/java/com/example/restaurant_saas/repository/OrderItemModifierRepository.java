package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.OrderItemModifier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface OrderItemModifierRepository extends JpaRepository<OrderItemModifier, UUID> {
}
