package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.DiningArea;
import com.example.restaurant_saas.dto.request.CreateDiningAreaRequest;
import com.example.restaurant_saas.dto.request.ReorderDiningAreasRequest;
import com.example.restaurant_saas.dto.request.UpdateDiningAreaRequest;
import com.example.restaurant_saas.dto.response.DiningAreaResponse;
import com.example.restaurant_saas.repository.DiningAreaRepository;
import com.example.restaurant_saas.repository.RestaurantRepository;
import com.example.restaurant_saas.repository.RestaurantTableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DiningAreaService {

    private final DiningAreaRepository diningAreaRepository;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantTableRepository tableRepository;

    @Transactional(readOnly = true)
    public List<DiningAreaResponse> listAreas(UUID restaurantId) {
        return diningAreaRepository.findByRestaurantIdOrderByDisplayOrderAsc(restaurantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public DiningAreaResponse createArea(UUID restaurantId, CreateDiningAreaRequest request) {
        if (diningAreaRepository.existsByRestaurantIdAndNameIgnoreCase(restaurantId, request.getName())) {
            throw new IllegalArgumentException("A dining area with this name already exists.");
        }

        DiningArea area = DiningArea.builder()
                .restaurant(restaurantRepository.getReferenceById(restaurantId))
                .name(request.getName())
                .displayOrder((int) diningAreaRepository.countByRestaurantId(restaurantId))
                .build();

        return toResponse(diningAreaRepository.save(area));
    }

    @Transactional
    public DiningAreaResponse updateArea(UUID restaurantId, UUID areaId, UpdateDiningAreaRequest request) {
        DiningArea area = findByIdAndRestaurant(restaurantId, areaId);

        if (diningAreaRepository.existsByRestaurantIdAndNameIgnoreCaseAndIdNot(restaurantId, request.getName(), areaId)) {
            throw new IllegalArgumentException("A dining area with this name already exists.");
        }
        area.setName(request.getName());

        return toResponse(diningAreaRepository.save(area));
    }

    @Transactional
    public void deleteArea(UUID restaurantId, UUID areaId) {
        DiningArea area = findByIdAndRestaurant(restaurantId, areaId);
        if (tableRepository.existsByAreaIdAndRestaurantId(areaId, restaurantId)) {
            throw new IllegalStateException("Cannot delete an area that still has tables assigned.");
        }
        diningAreaRepository.delete(area);
    }

    @Transactional
    public List<DiningAreaResponse> reorderAreas(UUID restaurantId, ReorderDiningAreasRequest request) {
        List<DiningArea> areas = diningAreaRepository.findByRestaurantIdOrderByDisplayOrderAsc(restaurantId);

        Set<UUID> existingIds = areas.stream().map(DiningArea::getId).collect(Collectors.toSet());
        Set<UUID> requestedIds = Set.copyOf(request.getOrderedIds());
        if (!existingIds.equals(requestedIds)) {
            throw new IllegalArgumentException("orderedIds must contain exactly the restaurant's dining areas.");
        }

        Map<UUID, DiningArea> byId = areas.stream().collect(Collectors.toMap(DiningArea::getId, a -> a));
        List<UUID> orderedIds = request.getOrderedIds();
        for (int i = 0; i < orderedIds.size(); i++) {
            byId.get(orderedIds.get(i)).setDisplayOrder(i);
        }

        return diningAreaRepository.saveAll(areas).stream()
                .sorted(Comparator.comparing(DiningArea::getDisplayOrder))
                .map(this::toResponse)
                .toList();
    }

    private DiningArea findByIdAndRestaurant(UUID restaurantId, UUID areaId) {
        return diningAreaRepository.findByIdAndRestaurantId(areaId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Dining area not found."));
    }

    private DiningAreaResponse toResponse(DiningArea area) {
        return DiningAreaResponse.builder()
                .id(area.getId())
                .restaurantId(area.getRestaurant().getId())
                .name(area.getName())
                .displayOrder(area.getDisplayOrder())
                .build();
    }
}
