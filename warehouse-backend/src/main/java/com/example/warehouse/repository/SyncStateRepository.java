package com.example.warehouse.repository;

import com.example.warehouse.domain.entity.SyncState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SyncStateRepository extends JpaRepository<SyncState, UUID> {
}
