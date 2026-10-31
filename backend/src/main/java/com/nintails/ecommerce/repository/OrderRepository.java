package com.nintails.ecommerce.repository;

import com.nintails.ecommerce.entity.Order;
import com.nintails.ecommerce.entity.Order.OrderStatus;
import com.nintails.ecommerce.entity.Order.PaymentMethod;
import com.nintails.ecommerce.entity.Order.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserIdOrderByPlacedAtDesc(Long userId);

    Page<Order> findByUserIdOrderByPlacedAtDesc(Long userId, Pageable pageable);

    Optional<Order> findByOrderNumber(String orderNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.razorpayOrderId = :orderId and o.user.id = :userId")
    Optional<Order> findByRazorpayOrderIdAndUserIdForUpdate(
            @Param("orderId") String orderId, @Param("userId") Long userId);

    Optional<Order> findFirstByUserIdAndPaymentMethodAndPaymentStatusAndStatusOrderByPlacedAtDesc(
            Long userId, PaymentMethod paymentMethod, PaymentStatus paymentStatus, OrderStatus status);

    Optional<Order> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    boolean existsByOrderNumber(String orderNumber);

    @Override
    @EntityGraph(attributePaths = "user")
    Page<Order> findAll(Pageable pageable);

    List<Order> findAllByPlacedAtAfter(LocalDateTime placedAt);

    List<Order> findAllByPlacedAtAfterAndPaymentStatus(
            LocalDateTime placedAt, PaymentStatus paymentStatus);

    long countByStatus(OrderStatus status);

    long countByPaymentMethod(PaymentMethod paymentMethod);

    @Query("select coalesce(sum(o.total), 0) from Order o where o.paymentStatus = :status")
    java.math.BigDecimal sumTotalByPaymentStatus(@Param("status") PaymentStatus status);
}
