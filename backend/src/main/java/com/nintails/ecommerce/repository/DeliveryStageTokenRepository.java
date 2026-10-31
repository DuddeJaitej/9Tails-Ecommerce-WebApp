package com.nintails.ecommerce.repository;

import com.nintails.ecommerce.entity.DeliveryStageToken;
import com.nintails.ecommerce.entity.DeliveryStageToken.Stage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DeliveryStageTokenRepository extends JpaRepository<DeliveryStageToken, Long> {

    Optional<DeliveryStageToken> findTopByFulfillmentIdAndStageOrderByIssuedAtDesc(
            Long fulfillmentId, Stage stage);

    java.util.List<DeliveryStageToken> findByFulfillmentId(Long fulfillmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from DeliveryStageToken t join fetch t.fulfillment f " +
           "join fetch f.order where t.tokenHash = :hash")
    Optional<DeliveryStageToken> findByTokenHashForUpdate(@Param("hash") String hash);

    @Query("select t from DeliveryStageToken t join fetch t.fulfillment f " +
           "join fetch f.order where t.tokenHash = :hash")
    Optional<DeliveryStageToken> findByTokenHash(@Param("hash") String hash);
}
