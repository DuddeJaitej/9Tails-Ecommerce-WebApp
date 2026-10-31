package com.nintails.ecommerce.repository;

import com.nintails.ecommerce.entity.AdminPasswordResetToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface AdminPasswordResetTokenRepository
        extends JpaRepository<AdminPasswordResetToken, Long> {

    Optional<AdminPasswordResetToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from AdminPasswordResetToken token where token.tokenHash = :tokenHash")
    Optional<AdminPasswordResetToken> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash);

    void deleteByUserId(Long userId);

    void deleteByExpiresAtBefore(LocalDateTime expiresAt);
}
