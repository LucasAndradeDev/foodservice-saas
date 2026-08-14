package com.example.warehouse.repository;

import com.example.warehouse.domain.entity.PurchaseItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PurchaseItemRepository extends JpaRepository<PurchaseItem, UUID> {
    List<PurchaseItem> findByPurchaseId(UUID purchaseId);
    List<PurchaseItem> findByPurchaseIdIn(List<UUID> purchaseIds);
}
