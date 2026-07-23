package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.RestaurantTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RestaurantTableRepository extends JpaRepository<RestaurantTable, UUID> {
    List<RestaurantTable> findByRestaurantId(UUID restaurantId);
    Optional<RestaurantTable> findByIdAndRestaurantId(UUID id, UUID restaurantId);
    boolean existsByRestaurantIdAndNumber(UUID restaurantId, Integer number);
    boolean existsByRestaurantIdAndNumberAndIdNot(UUID restaurantId, Integer number, UUID id);

    @Query("SELECT COALESCE(MAX(t.number), 0) FROM RestaurantTable t WHERE t.restaurant.id = :restaurantId")
    Integer findMaxNumberByRestaurantId(@Param("restaurantId") UUID restaurantId);
}
