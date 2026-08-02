package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.CashWithdrawal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CashWithdrawalRepository extends JpaRepository<CashWithdrawal, UUID> {
    List<CashWithdrawal> findBySessionIdOrderByWithdrawnAtDesc(UUID sessionId);
}
