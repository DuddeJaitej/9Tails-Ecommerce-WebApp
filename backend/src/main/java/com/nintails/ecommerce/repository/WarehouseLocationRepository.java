package com.nintails.ecommerce.repository;

import com.nintails.ecommerce.entity.WarehouseLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WarehouseLocationRepository extends JpaRepository<WarehouseLocation, Long> {
    List<WarehouseLocation> findByActiveTrueOrderByNameAsc();
    List<WarehouseLocation> findAllByOrderByNameAsc();
    Optional<WarehouseLocation> findByIdAndActiveTrue(Long id);
    Optional<WarehouseLocation> findByOperatorId(Long operatorId);
    Optional<WarehouseLocation> findByArrivalQrTokenHashAndActiveTrue(String arrivalQrTokenHash);
}
