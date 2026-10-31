package com.nintails.ecommerce.repository;

import com.nintails.ecommerce.entity.PhoneVerificationChallenge;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PhoneVerificationChallengeRepository extends JpaRepository<PhoneVerificationChallenge, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PhoneVerificationChallenge c where c.id = :id")
    Optional<PhoneVerificationChallenge> findByIdForUpdate(@Param("id") Long id);

    long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime createdAt);
}
