package com.nintails.ecommerce.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductVersionTest {

    @Test
    void newProductsStartWithAnOptimisticLockVersion() {
        Product product = Product.builder().build();

        assertEquals(0L, product.getVersion());
    }
}
