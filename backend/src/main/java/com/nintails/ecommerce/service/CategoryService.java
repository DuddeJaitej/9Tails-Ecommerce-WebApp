package com.nintails.ecommerce.service;

import com.nintails.ecommerce.dto.ApiResponse;
import com.nintails.ecommerce.entity.Category;
import com.nintails.ecommerce.exception.BadRequestException;
import com.nintails.ecommerce.exception.ResourceNotFoundException;
import com.nintails.ecommerce.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<Category> getAll() {
        return categoryRepository.findAll();
    }

    public Category getById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }

    @Transactional
    public Category create(Category category) {
        if (categoryRepository.existsByNameIgnoreCase(category.getName())) {
            throw new BadRequestException("Category already exists: " + category.getName());
        }
        return categoryRepository.save(category);
    }

    @Transactional
    public Category update(Long id, Category updated) {
        Category existing = getById(id);
        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setImageUrl(updated.getImageUrl());
        return categoryRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
        categoryRepository.deleteById(id);
    }
}
