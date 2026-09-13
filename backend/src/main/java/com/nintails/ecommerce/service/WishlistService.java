package com.nintails.ecommerce.service;

import com.nintails.ecommerce.dto.ProductDto;
import com.nintails.ecommerce.entity.Product;
import com.nintails.ecommerce.entity.User;
import com.nintails.ecommerce.entity.WishlistItem;
import com.nintails.ecommerce.exception.BadRequestException;
import com.nintails.ecommerce.exception.ResourceNotFoundException;
import com.nintails.ecommerce.repository.ProductRepository;
import com.nintails.ecommerce.repository.UserRepository;
import com.nintails.ecommerce.repository.WishlistItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WishlistService {

    private final WishlistItemRepository wishlistItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ProductService productService;

    public List<ProductDto> getWishlist(String email) {
        User user = getUser(email);
        return wishlistItemRepository.findByUserId(user.getId())
                .stream()
                .map(wi -> productService.toDto(wi.getProduct()))
                .toList();
    }

    @Transactional
    public String toggleWishlist(String email, Long productId) {
        User user = getUser(email);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));

        if (wishlistItemRepository.existsByUserIdAndProductId(user.getId(), productId)) {
            wishlistItemRepository.deleteByUserIdAndProductId(user.getId(), productId);
            return "removed";
        } else {
            WishlistItem item = WishlistItem.builder()
                    .user(user).product(product).build();
            wishlistItemRepository.save(item);
            return "added";
        }
    }

    public boolean isWishlisted(String email, Long productId) {
        User user = getUser(email);
        return wishlistItemRepository.existsByUserIdAndProductId(user.getId(), productId);
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
