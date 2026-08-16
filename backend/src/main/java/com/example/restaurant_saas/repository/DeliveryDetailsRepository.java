package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.DeliveryDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeliveryDetailsRepository extends JpaRepository<DeliveryDetails, UUID> {

    Optional<DeliveryDetails> findByTab_Id(UUID tabId);
}
