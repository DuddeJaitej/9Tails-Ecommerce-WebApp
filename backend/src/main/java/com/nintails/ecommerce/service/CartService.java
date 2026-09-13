package com.nintails.ecommerce.service;

import com.nintails.ecommerce.dto.CartItemDto;
import com.nintails.ecommerce.dto.CartSummaryDto;
import com.nintails.ecommerce.entity.CartItem;
import com.nintails.ecommerce.entity.Product;
import com.nintails.ecommerce.entity.User;
import com.nintails.ecommerce.exception.BadRequestException;
import com.nintails.ecommerce.exception.ResourceNotFoundException;
import com.nintails.ecommerce.repository.CartItemRepository;
import com.nintails.ecommerce.repository.ProductRepository;
import com.nintails.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    private static final BigDecimal FREE_DELIVERY_THRESHOLD = new BigDecimal("500");
    private static final BigDecimal DELIVERY_CHARGE = new BigDecimal("10");

    public CartSummaryDto getCart(String email) {
        User user = getUser(email);
        List<CartItemDto> items = cartItemRepository.findByUserId(user.getId())
                .stream().map(this::toDto).toList();
        return buildSummary(items);
    }

    @Transactional
    public CartSummaryDto addItem(String email, Long productId, int quantity) {
        User user = getUser(email);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));

        if (!product.getActive()) {
            throw new BadRequestException("Product is not available");
        }
        if (product.getStock() < quantity) {
            throw new BadRequestException("Insufficient stock. Available: " + product.getStock());
        }

        CartItem item = cartItemRepository.findByUserIdAndProductId(user.getId(), productId)
                .orElse(CartItem.builder().user(user).product(product).quantity(0).build());

        item.setQuantity(item.getQuantity() + quantity);
        cartItemRepository.save(item);

        return getCart(email);
    }

    @Transactional
    public CartSummaryDto updateQuantity(String email, Long productId, int quantity) {
        User user = getUser(email);
        CartItem item = cartItemRepository.findByUserIdAndProductId(user.getId(), productId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));

        if (quantity <= 0) {
            cartItemRepository.delete(item);
        } else {
            Product product = item.getProduct();
            if (product.getStock() < quantity) {
                throw new BadRequestException("Insufficient stock. Available: " + product.getStock());
            }
            item.setQuantity(quantity);
            cartItemRepository.save(item);
        }

        return getCart(email);
    }

    @Transactional
    public CartSummaryDto removeItem(String email, Long productId) {
        User user = getUser(email);
        cartItemRepository.deleteByUserIdAndProductId(user.getId(), productId);
        return getCart(email);
    }

    @Transactional
    public void clearCart(String email) {
        User user = getUser(email);
        cartItemRepository.deleteByUserId(user.getId());
    }

    private CartSummaryDto buildSummary(List<CartItemDto> items) {
        BigDecimal subtotal = items.stream()
                .map(CartItemDto::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal delivery = subtotal.compareTo(FREE_DELIVERY_THRESHOLD) > 0
                ? BigDecimal.ZERO : DELIVERY_CHARGE;

        return CartSummaryDto.builder()
                .items(items)
                .totalItems(items.stream().mapToInt(CartItemDto::getQuantity).sum())
                .subtotal(subtotal)
                .deliveryCharge(delivery)
                .total(subtotal.add(delivery))
                .build();
    }

    private CartItemDto toDto(CartItem ci) {
        Product p = ci.getProduct();
        BigDecimal lineTotal = p.getPrice()
                .multiply(BigDecimal.valueOf(ci.getQuantity()));
        return CartItemDto.builder()
                .id(ci.getId())
                .productId(p.getId())
                .productName(p.getName())
                .productImageUrl(p.getImageUrl())
                .categoryName(p.getCategory().getName())
                .unitPrice(p.getPrice())
                .quantity(ci.getQuantity())
                .lineTotal(lineTotal)
                .inStock(p.getStock() > 0)
                .build();
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
