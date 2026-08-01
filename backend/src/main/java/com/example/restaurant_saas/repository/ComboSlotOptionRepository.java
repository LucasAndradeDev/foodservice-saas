package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.ComboSlotOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ComboSlotOptionRepository extends JpaRepository<ComboSlotOption, UUID> {
    List<ComboSlotOption> findBySlotIdOrderByCreatedAtAsc(UUID slotId);
    List<ComboSlotOption> findBySlotIdInOrderByCreatedAtAsc(List<UUID> slotIds);
    boolean existsByProductId(UUID productId);
}
