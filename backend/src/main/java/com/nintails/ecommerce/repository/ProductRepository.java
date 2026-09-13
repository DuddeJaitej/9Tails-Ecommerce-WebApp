package com.nintails.ecommerce.repository;

import com.nintails.ecommerce.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findByActiveTrue(Pageable pageable);

    Page<Product> findByCategoryIdAndActiveTrue(Long categoryId, Pageable pageable);

    Page<Product> findByCategoryNameIgnoreCaseAndActiveTrue(String categoryName, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.active = true AND " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           " LOWER(p.description) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<Product> searchProducts(@Param("q") String query, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.active = true AND " +
           "p.price BETWEEN :min AND :max")
    Page<Product> findByPriceRange(@Param("min") BigDecimal min,
                                   @Param("max") BigDecimal max,
                                   Pageable pageable);

    List<Product> findTop8ByCategoryNameIgnoreCaseAndActiveTrueAndIdNot(
            String categoryName, Long excludeId);
}
