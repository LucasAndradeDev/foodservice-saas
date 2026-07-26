package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.TableRequest;
import com.example.restaurant_saas.dto.response.TableRequestResponse;
import com.example.restaurant_saas.repository.TableRequestRepository;
import com.example.restaurant_saas.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TableRequestService {

    private final TableRequestRepository tableRequestRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<TableRequestResponse> listPending(UUID restaurantId) {
        return tableRequestRepository.findByRestaurantIdAndAcknowledgedAtIsNull(restaurantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public TableRequestResponse acknowledge(UUID restaurantId, UUID requestId, UUID userId) {
        TableRequest request = tableRequestRepository.findByIdAndRestaurantId(requestId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found."));

        request.setAcknowledgedAt(OffsetDateTime.now());
        request.setAcknowledgedBy(userRepository.getReferenceById(userId));

        return toResponse(tableRequestRepository.save(request));
    }

    private TableRequestResponse toResponse(TableRequest request) {
        return TableRequestResponse.builder()
                .id(request.getId())
                .tableId(request.getTable().getId())
                .type(request.getType())
                .requestedAt(request.getRequestedAt())
                .acknowledgedAt(request.getAcknowledgedAt())
                .build();
    }
}
