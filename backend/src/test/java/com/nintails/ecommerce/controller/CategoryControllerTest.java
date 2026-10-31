package com.nintails.ecommerce.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nintails.ecommerce.entity.Category;
import com.nintails.ecommerce.service.CategoryService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CategoryControllerTest {

    @Test
    void categoryListReturnsJsonSafeOptionsWithoutProductGraph() throws Exception {
        CategoryService categoryService = mock(CategoryService.class);
        when(categoryService.getAll()).thenReturn(List.of(Category.builder()
                .id(1L)
                .name("Fashion")
                .description("Clothing and apparel")
                .imageUrl("fashion.jpg")
                .build()));

        String response = new ObjectMapper().findAndRegisterModules().writeValueAsString(
                new CategoryController(categoryService).getAll().getBody());

        assertTrue(response.contains("\"name\":\"Fashion\""));
        assertTrue(response.contains("\"id\":1"));
        assertFalse(response.contains("\"products\""));
    }
}
