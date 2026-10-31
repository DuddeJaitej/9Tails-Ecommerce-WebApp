package com.nintails.ecommerce.service;

import com.nintails.ecommerce.dto.WarehouseLocationDto;
import com.nintails.ecommerce.entity.WarehouseLocation;
import com.nintails.ecommerce.exception.ResourceNotFoundException;
import com.nintails.ecommerce.service.NotificationService;
import com.nintails.ecommerce.repository.WarehouseLocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WarehouseLocationService {

    private final WarehouseLocationRepository repository;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<WarehouseLocationDto> listActive() {
        return repository.findByActiveTrueOrderByNameAsc().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WarehouseLocationDto> listAll() {
        return repository.findAllByOrderByNameAsc().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public WarehouseLocationDto setActive(Long id, boolean active) {
        WarehouseLocation location = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse location", id));
        location.setActive(active);
        WarehouseLocation saved = repository.save(location);
        if (saved.getOperator() != null) {
            notificationService.notify(saved.getOperator(), null, null,
                    active ? "WAREHOUSE_APPROVED" : "WAREHOUSE_DEACTIVATED",
                    active ? "Warehouse approved" : "Warehouse deactivated",
                    active ? "Your warehouse has been approved. You can now manage delivery operations."
                            : "Your warehouse is inactive. Contact an administrator for details.");
        }
        return toDto(saved);
    }

    private WarehouseLocationDto toDto(WarehouseLocation location) {
        return WarehouseLocationDto.builder()
                .id(location.getId())
                .name(location.getName())
                .street(location.getStreet())
                .city(location.getCity())
                .state(location.getState())
                .postalCode(location.getPostalCode())
                .latitude(location.getLatitude())
                .longitude(location.getLongitude())
                .active(Boolean.TRUE.equals(location.getActive()))
                .operatorId(location.getOperator() == null ? null : location.getOperator().getId())
                .operatorName(location.getOperator() == null ? null : location.getOperator().getFullName())
                .operatorEmail(location.getOperator() == null ? null : location.getOperator().getEmail())
                .build();
    }
}
