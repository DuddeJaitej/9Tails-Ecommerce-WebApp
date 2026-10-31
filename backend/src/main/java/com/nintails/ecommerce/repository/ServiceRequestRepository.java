package com.nintails.ecommerce.repository;

import com.nintails.ecommerce.entity.ServiceRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, Long> {
    List<ServiceRequest> findByOrderItem_Order_IdOrderByRequestedAtDesc(Long orderId);
    List<ServiceRequest> findByOrderItem_Fulfillment_IdOrderByRequestedAtDesc(Long fulfillmentId);
    List<ServiceRequest> findByDeliveryAgent_IdOrderByRequestedAtDesc(Long agentId);
    List<ServiceRequest> findByWarehouse_IdOrderByRequestedAtAsc(Long warehouseId);
    Optional<ServiceRequest> findByIdAndWarehouse_Id(Long id, Long warehouseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from ServiceRequest request where request.id = :id")
    Optional<ServiceRequest> findByIdForUpdate(@Param("id") Long id);
}
