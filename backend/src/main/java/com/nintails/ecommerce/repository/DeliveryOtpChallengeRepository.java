package com.nintails.ecommerce.repository;

import com.nintails.ecommerce.entity.DeliveryOtpChallenge;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.time.LocalDateTime;

public interface DeliveryOtpChallengeRepository extends JpaRepository<DeliveryOtpChallenge, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from DeliveryOtpChallenge c where c.id = :id")
    Optional<DeliveryOtpChallenge> findByIdForUpdate(@Param("id") Long id);

    Optional<DeliveryOtpChallenge> findTopByFulfillmentIdAndConsumedAtIsNullOrderByCreatedAtDesc(
            Long fulfillmentId);

    long countByFulfillmentIdAndCreatedAtAfter(Long fulfillmentId, LocalDateTime createdAt);
}
