package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.PostMealFeedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PostMealFeedbackRepository extends JpaRepository<PostMealFeedback, UUID> {
    boolean existsByTabId(UUID tabId);
    Optional<PostMealFeedback> findByTabId(UUID tabId);
    List<PostMealFeedback> findByRestaurantIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            UUID restaurantId, OffsetDateTime start, OffsetDateTime end);
    Page<PostMealFeedback> findByRestaurantIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            UUID restaurantId, OffsetDateTime start, OffsetDateTime end, Pageable pageable);
}
