package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.OrderItem;
import com.example.restaurant_saas.domain.enums.ItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
    Optional<OrderItem> findByIdAndOrder_Restaurant_Id(UUID id, UUID restaurantId);
    List<OrderItem> findByOrder_Restaurant_IdAndStatusInOrderByCreatedAtAsc(UUID restaurantId, List<ItemStatus> statuses);
    boolean existsByOrder_Tab_IdAndStatusNotIn(UUID tabId, List<ItemStatus> statuses);
}
