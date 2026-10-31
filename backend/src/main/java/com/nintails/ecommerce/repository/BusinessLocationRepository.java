package com.nintails.ecommerce.repository;

import com.nintails.ecommerce.entity.BusinessLocation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BusinessLocationRepository extends JpaRepository<BusinessLocation, Long> {
    List<BusinessLocation> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
    Optional<BusinessLocation> findByIdAndOwnerId(Long id, Long ownerId);

    @EntityGraph(attributePaths = "owner")
    List<BusinessLocation> findByVerifiedByOwnerTrueOrderByCityAscNameAsc();

    @EntityGraph(attributePaths = "owner")
    Optional<BusinessLocation> findByIdAndVerifiedByOwnerTrue(Long id);
}
