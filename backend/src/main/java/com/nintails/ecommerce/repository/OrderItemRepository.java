package com.nintails.ecommerce.repository;

import com.nintails.ecommerce.dto.CustomerReviewDto;
import com.nintails.ecommerce.dto.ProductRatingSummary;
import com.nintails.ecommerce.entity.OrderItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    Optional<OrderItem> findByIdAndOrder_IdAndOrder_User_Id(
            Long itemId, Long orderId, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from OrderItem item where item.id = :itemId "
            + "and item.order.id = :orderId and item.order.user.id = :userId")
    Optional<OrderItem> findByIdAndOrderAndUserForUpdate(
            @Param("itemId") Long itemId,
            @Param("orderId") Long orderId,
            @Param("userId") Long userId);

    @Query("select new com.nintails.ecommerce.dto.ProductRatingSummary("
            + "item.product.id, avg(item.customerRating), count(item.id)) "
            + "from OrderItem item where item.product.id = :productId "
            + "and item.customerRating is not null group by item.product.id")
    Optional<ProductRatingSummary> findRatingSummaryByProductId(
            @Param("productId") Long productId);

    @Query("select new com.nintails.ecommerce.dto.ProductRatingSummary("
            + "item.product.id, avg(item.customerRating), count(item.id)) "
            + "from OrderItem item where item.product.id in :productIds "
            + "and item.customerRating is not null group by item.product.id")
    List<ProductRatingSummary> findRatingSummariesByProductIds(
            @Param("productIds") Collection<Long> productIds);

    @Query("select new com.nintails.ecommerce.dto.ProductRatingSummary("
            + "item.product.id, avg(item.customerRating), count(item.id)) "
            + "from OrderItem item join item.product product "
            + "where product.active = true and item.customerRating is not null "
            + "group by item.product.id "
            + "order by avg(item.customerRating) desc, count(item.id) desc")
    List<ProductRatingSummary> findTopRatedProducts(Pageable pageable);

    @Query("select new com.nintails.ecommerce.dto.CustomerReviewDto("
            + "item.id, item.customerReview, item.customerRating, "
            + "item.order.user.fullName, "
            + "item.order.user.profilePhotoUrl) "
            + "from OrderItem item where item.customerRating is not null "
            + "and item.customerReview is not null "
            + "order by item.customerReviewedAt desc, item.id desc")
    List<CustomerReviewDto> findLatestCustomerReviews(Pageable pageable);
}
