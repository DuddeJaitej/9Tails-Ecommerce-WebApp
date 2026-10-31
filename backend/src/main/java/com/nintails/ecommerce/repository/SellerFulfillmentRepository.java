package com.nintails.ecommerce.repository;

import com.nintails.ecommerce.entity.SellerFulfillment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface SellerFulfillmentRepository extends JpaRepository<SellerFulfillment, Long> {

    @EntityGraph(attributePaths = {"order", "owner", "deliveryAgent", "transferDeliveryAgent", "finalDeliveryAgent", "pickupLocation", "items", "items.product"})
    List<SellerFulfillment> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    @EntityGraph(attributePaths = {"order", "owner", "deliveryAgent", "transferDeliveryAgent", "finalDeliveryAgent", "pickupLocation", "destinationStore", "items", "items.product"})
    List<SellerFulfillment> findByDestinationStore_Owner_IdOrderByCreatedAtDesc(Long ownerId);

    @EntityGraph(attributePaths = {"order", "owner", "deliveryAgent", "transferDeliveryAgent", "finalDeliveryAgent", "pickupLocation", "warehouseLocation", "destinationWarehouseLocation", "items", "items.product"})
    List<SellerFulfillment> findByWarehouseLocationIdOrDestinationWarehouseLocationIdOrderByCreatedAtAsc(
            Long originWarehouseId, Long destinationWarehouseId);

    @EntityGraph(attributePaths = {"order", "owner", "deliveryAgent", "transferDeliveryAgent", "finalDeliveryAgent", "pickupLocation", "warehouseLocation", "destinationWarehouseLocation", "items", "items.product"})
    List<SellerFulfillment> findByDeliveryAgentIdOrderByCreatedAtDesc(Long deliveryAgentId);

    @EntityGraph(attributePaths = {"order", "owner", "deliveryAgent", "transferDeliveryAgent", "finalDeliveryAgent", "pickupLocation", "warehouseLocation", "destinationWarehouseLocation", "items", "items.product"})
    List<SellerFulfillment> findByFinalDeliveryAgentIdOrderByCreatedAtDesc(Long deliveryAgentId);

    @EntityGraph(attributePaths = {"order", "owner", "deliveryAgent", "transferDeliveryAgent", "finalDeliveryAgent", "pickupLocation", "warehouseLocation", "destinationWarehouseLocation", "items", "items.product"})
    List<SellerFulfillment> findByTransferDeliveryAgentIdOrderByCreatedAtDesc(Long deliveryAgentId);

    List<SellerFulfillment> findByTransferAgentAcceptedAtIsNullAndTransferAgentOfferExpiresAtBefore(
            java.time.LocalDateTime now);
    List<SellerFulfillment> findByTransferAgentAcceptedAtIsNotNullAndTransferAgentRouteDeadlineBefore(
            java.time.LocalDateTime now);

    List<SellerFulfillment> findByDeliveryAgentIdAndDeliveryAgentAcceptedAtIsNullAndDeliveryAgentOfferExpiresAtBefore(
            Long deliveryAgentId, java.time.LocalDateTime now);
    List<SellerFulfillment> findByDeliveryAgentAcceptedAtIsNullAndDeliveryAgentOfferExpiresAtBefore(
            java.time.LocalDateTime now);
    List<SellerFulfillment> findByDeliveryAgentAcceptedAtIsNotNullAndDeliveryAgentRouteDeadlineBefore(
            java.time.LocalDateTime now);
    List<SellerFulfillment> findByFinalDeliveryAgentIdAndFinalAgentAcceptedAtIsNullAndFinalAgentOfferExpiresAtBefore(
            Long deliveryAgentId, java.time.LocalDateTime now);
    List<SellerFulfillment> findByFinalAgentAcceptedAtIsNullAndFinalAgentOfferExpiresAtBefore(
            java.time.LocalDateTime now);
    List<SellerFulfillment> findByFinalAgentAcceptedAtIsNotNullAndFinalAgentRouteDeadlineBefore(
            java.time.LocalDateTime now);

    @EntityGraph(attributePaths = {"order", "owner", "deliveryAgent", "transferDeliveryAgent", "finalDeliveryAgent", "warehouseLocation", "destinationWarehouseLocation", "items", "items.product"})
    List<SellerFulfillment> findAllByOrderId(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from SellerFulfillment f where f.id = :id")
    Optional<SellerFulfillment> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"order", "owner", "deliveryAgent", "transferDeliveryAgent", "finalDeliveryAgent", "items", "items.product"})
    Optional<SellerFulfillment> findByIdAndOwnerId(Long id, Long ownerId);

    @EntityGraph(attributePaths = {"order", "owner", "deliveryAgent", "transferDeliveryAgent", "finalDeliveryAgent", "items", "items.product"})
    Optional<SellerFulfillment> findByIdAndDeliveryAgentId(Long id, Long agentId);

    @EntityGraph(attributePaths = {"order", "owner", "deliveryAgent", "transferDeliveryAgent", "finalDeliveryAgent", "warehouseLocation", "destinationWarehouseLocation", "items", "items.product"})
    List<SellerFulfillment> findByStatusInOrderByCreatedAtAsc(Collection<SellerFulfillment.FulfillmentStatus> statuses);
}
