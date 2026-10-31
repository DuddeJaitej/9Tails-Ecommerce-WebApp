package com.nintails.ecommerce.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeliveryOfferExpiryScheduler {

    private final DeliveryWorkflowService deliveryWorkflowService;

    @Scheduled(fixedDelayString = "${app.delivery.offer-expiry-check-ms:15000}")
    public void reassignExpiredOffers() {
        deliveryWorkflowService.expireOffers();
    }
}
