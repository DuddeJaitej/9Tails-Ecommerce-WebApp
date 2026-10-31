package com.nintails.ecommerce.repository;

import com.nintails.ecommerce.entity.DeliveryEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;

public interface DeliveryEventRepository extends JpaRepository<DeliveryEvent, Long> {
    List<DeliveryEvent> findByFulfillmentIdOrderByCreatedAtAsc(Long fulfillmentId);
    List<DeliveryEvent> findByFulfillmentIdAndEventType(Long fulfillmentId, String eventType);

    @EntityGraph(attributePaths = {"fulfillment", "fulfillment.order", "actor"})
    List<DeliveryEvent> findTop100ByOrderByCreatedAtDesc();
}
