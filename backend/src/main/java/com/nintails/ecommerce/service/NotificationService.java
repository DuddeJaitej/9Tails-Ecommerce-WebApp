package com.nintails.ecommerce.service;

import com.nintails.ecommerce.dto.NotificationDto;
import com.nintails.ecommerce.dto.NotificationSummaryDto;
import com.nintails.ecommerce.entity.Notification;
import com.nintails.ecommerce.entity.Order;
import com.nintails.ecommerce.entity.SellerFulfillment;
import com.nintails.ecommerce.entity.User;
import com.nintails.ecommerce.exception.ResourceNotFoundException;
import com.nintails.ecommerce.repository.NotificationRepository;
import com.nintails.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Transactional
    public void notify(User recipient, Order order, SellerFulfillment fulfillment,
                       String type, String title, String message) {
        if (recipient == null) {
            return;
        }
        notificationRepository.save(Notification.builder()
                .recipient(recipient)
                .order(order)
                .fulfillment(fulfillment)
                .type(type)
                .title(title)
                .message(message)
                .actionUrl(order == null ? null : "Orders.html?id=" + order.getId())
                .build());
    }

    @Transactional
    public void notifyAdmins(List<User> recipients, Order order, SellerFulfillment fulfillment,
                             String type, String title, String message) {
        recipients.stream()
                .filter(user -> user.getRole() == User.Role.ADMIN)
                .forEach(admin -> notify(admin, order, fulfillment, type, title, message));
    }

    @Transactional(readOnly = true)
    public NotificationSummaryDto getForUser(String email, int size) {
        User user = findUserByEmail(email);
        int boundedSize = Math.max(1, Math.min(size, 100));
        List<NotificationDto> notifications = notificationRepository
                .findByRecipientIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(0, boundedSize))
                .map(this::toDto).getContent();
        return NotificationSummaryDto.builder()
                .notifications(notifications)
                .unreadCount(notificationRepository.countByRecipientIdAndReadAtIsNull(user.getId()))
                .build();
    }

    @Transactional
    public NotificationDto markRead(String email, Long notificationId) {
        User user = findUserByEmail(email);
        Notification notification = notificationRepository.findById(notificationId)
                .filter(item -> item.getRecipient().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));
        if (notification.getReadAt() == null) {
            notification.setReadAt(java.time.LocalDateTime.now());
        }
        return toDto(notificationRepository.save(notification));
    }

    @Transactional
    public long markAllRead(String email) {
        User user = findUserByEmail(email);
        return notificationRepository.markAllRead(user.getId(), java.time.LocalDateTime.now());
    }

    private NotificationDto toDto(Notification notification) {
        Order order = notification.getOrder();
        return NotificationDto.builder()
                .id(notification.getId())
                .type(notification.getType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .orderId(order == null ? null : order.getId())
                .orderNumber(order == null ? null : order.getOrderNumber())
                .actionUrl(notification.getActionUrl())
                .read(notification.getReadAt() != null)
                .createdAt(notification.getCreatedAt())
                .build();
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
