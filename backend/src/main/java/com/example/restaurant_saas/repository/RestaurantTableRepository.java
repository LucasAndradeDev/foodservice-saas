package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.RestaurantTable;
import com.example.restaurant_saas.domain.enums.TableStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
    List<RestaurantTable> findByIdInAndRestaurantId(List<UUID> ids, UUID restaurantId);
    boolean existsByRestaurantIdAndNumber(UUID restaurantId, Integer number);
    boolean existsByRestaurantIdAndNumberAndIdNot(UUID restaurantId, Integer number, UUID id);
    long countByRestaurantIdAndStatus(UUID restaurantId, TableStatus status);
    boolean existsByAreaIdAndRestaurantId(UUID areaId, UUID restaurantId);

    @Query("SELECT t.number FROM RestaurantTable t WHERE t.restaurant.id = :restaurantId ORDER BY t.number ASC")
    List<Integer> findAllNumbersByRestaurantId(@Param("restaurantId") UUID restaurantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM RestaurantTable t WHERE t.id = :id AND t.restaurant.id = :restaurantId")
    Optional<RestaurantTable> findByIdAndRestaurantIdForUpdate(@Param("id") UUID id, @Param("restaurantId") UUID restaurantId);
}
