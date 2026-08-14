package com.example.warehouse.service;

import com.example.warehouse.domain.entity.Supplier;
import com.example.warehouse.dto.request.CreateSupplierRequest;
import com.example.warehouse.dto.request.UpdateSupplierRequest;
import com.example.warehouse.dto.response.SupplierResponse;
import com.example.warehouse.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;

    @Transactional(readOnly = true)
    public List<SupplierResponse> listSuppliers(UUID restaurantLinkId, Boolean activeFilter) {
        return supplierRepository.findByRestaurantLinkIdOrderByNameAsc(restaurantLinkId).stream()
                .filter(supplier -> activeFilter == null || activeFilter.equals(supplier.getActive()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SupplierResponse getSupplier(UUID restaurantLinkId, UUID supplierId) {
        return toResponse(findByIdAndRestaurantLink(restaurantLinkId, supplierId));
    }

    @Transactional
    public SupplierResponse createSupplier(UUID restaurantLinkId, CreateSupplierRequest request) {
        if (supplierRepository.existsByRestaurantLinkIdAndNameIgnoreCase(restaurantLinkId, request.getName())) {
            throw new IllegalArgumentException("A supplier with this name already exists.");
        }

        Supplier supplier = Supplier.builder()
                .restaurantLinkId(restaurantLinkId)
                .name(request.getName())
                .contact(request.getContact())
                .active(true)
                .build();

        return toResponse(supplierRepository.save(supplier));
    }

    @Transactional
    public SupplierResponse updateSupplier(UUID restaurantLinkId, UUID supplierId, UpdateSupplierRequest request) {
        Supplier supplier = findByIdAndRestaurantLink(restaurantLinkId, supplierId);

        if (request.getName() != null) {
            if (supplierRepository.existsByRestaurantLinkIdAndNameIgnoreCaseAndIdNot(restaurantLinkId, request.getName(), supplierId)) {
                throw new IllegalArgumentException("A supplier with this name already exists.");
            }
            supplier.setName(request.getName());
        }
        if (request.getContact() != null) {
            supplier.setContact(request.getContact());
        }
        if (request.getActive() != null) {
            supplier.setActive(request.getActive());
        }

        return toResponse(supplierRepository.save(supplier));
    }

    private Supplier findByIdAndRestaurantLink(UUID restaurantLinkId, UUID supplierId) {
        return supplierRepository.findByIdAndRestaurantLinkId(supplierId, restaurantLinkId)
                .orElseThrow(() -> new IllegalArgumentException("Supplier not found."));
    }

    private SupplierResponse toResponse(Supplier supplier) {
        return SupplierResponse.builder()
                .id(supplier.getId())
                .name(supplier.getName())
                .contact(supplier.getContact())
                .active(supplier.getActive())
                .build();
    }
}
