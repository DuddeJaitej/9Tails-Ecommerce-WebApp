package com.nintails.ecommerce.service;

import com.nintails.ecommerce.dto.AdminAccountDto;
import com.nintails.ecommerce.dto.AdminAccountCreateRequest;
import com.nintails.ecommerce.dto.AdminPasswordResetEmailRequest;
import com.nintails.ecommerce.dto.AdminPasswordResetRequest;
import com.nintails.ecommerce.entity.AdminPasswordResetToken;
import com.nintails.ecommerce.entity.User;
import com.nintails.ecommerce.exception.BadRequestException;
import com.nintails.ecommerce.exception.ConflictException;
import com.nintails.ecommerce.exception.ExternalServiceUnavailableException;
import com.nintails.ecommerce.repository.AdminPasswordResetTokenRepository;
import com.nintails.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAccountService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int RESET_TOKEN_BYTES = 32;
    private static final int RESET_TOKEN_TTL_MINUTES = 30;
    private final UserRepository userRepository;
    private final AdminPasswordResetTokenRepository resetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.host:}")
    private String smtpHost;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    @Value("${app.admin.reset-url:http://localhost:5500/Home/Admin.html}")
    private String resetUrl;

    @Value("${app.customer.reset-url:http://localhost:5500/Home/Home.html}")
    private String customerResetUrl;

    @Value("${spring.mail.from:${spring.mail.username:}}")
    private String fromAddress;

    @Transactional
    public AdminAccountDto registerAdmin(AdminAccountCreateRequest request, String createdBy) {
        return registerAdmin(request.getEmail(), request.getPassword(), createdBy);
    }

    @Transactional(readOnly = true)
    public boolean isInitialAdminRegistrationAvailable() {
        return userRepository.countByRole(User.Role.ADMIN) == 0;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public AdminAccountDto registerInitialAdmin(AdminAccountCreateRequest request) {
        if (userRepository.countByRole(User.Role.ADMIN) > 0) {
            throw new ConflictException(
                    "Initial administrator registration is closed. Sign in or ask an administrator to create an account.");
        }
        return registerAdmin(request.getEmail(), request.getPassword(), "initial setup");
    }

    private AdminAccountDto registerAdmin(String rawEmail, String password, String createdBy) {
        String email = normalizeEmail(rawEmail);
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("An account already exists for this email address");
        }
        validatePasswordSize(password);

        User user = userRepository.save(User.builder()
                .fullName(nameFromEmail(email))
                .email(email)
                .password(passwordEncoder.encode(password))
                .role(User.Role.ADMIN)
                .build());
        log.info("Administrator account {} created by administrator {}.", user.getId(), createdBy);
        return toDto(user);
    }

    @Transactional
    public void requestPasswordReset(AdminPasswordResetEmailRequest request) {
        requestPasswordReset(request, User.Role.ADMIN, resetUrl, "administrator");
    }

    @Transactional
    public void requestCustomerPasswordReset(AdminPasswordResetEmailRequest request) {
        requestPasswordReset(request, User.Role.USER, customerResetUrl, "customer");
    }

    private void requestPasswordReset(AdminPasswordResetEmailRequest request, User.Role role,
            String targetResetUrl, String accountType) {
        String email = normalizeEmail(request.getEmail());
        User user = userRepository.findByEmail(email)
                .filter(account -> account.getRole() == role)
                .orElse(null);
        if (user == null) {
            return;
        }
        ensureSmtpConfigured();

        String rawToken = createToken();
        resetTokenRepository.deleteByUserId(user.getId());
        resetTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        resetTokenRepository.save(AdminPasswordResetToken.builder()
                .user(user)
                .tokenHash(hashToken(rawToken))
                .expiresAt(LocalDateTime.now().plusMinutes(RESET_TOKEN_TTL_MINUTES))
                .build());

        String link = UriComponentsBuilder.fromUriString(targetResetUrl)
                .queryParam("resetToken", rawToken)
                .build()
                .encode()
                .toUriString();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(user.getEmail());
        message.setSubject("Reset your 9Tails " + accountType + " password");
        message.setText("A password reset was requested for your 9Tails " + accountType + " account.\n\n"
                + "Use this one-time link within " + RESET_TOKEN_TTL_MINUTES
                + " minutes:\n" + link + "\n\n"
                + "If you did not request this, you can ignore this email.");
        try {
            mailSender.send(message);
        } catch (MailException ex) {
            resetTokenRepository.deleteByUserId(user.getId());
            log.error("Password reset email could not be sent for {} account.", accountType, ex);
            throw new ExternalServiceUnavailableException(
                    "Password reset email could not be sent. Check the SMTP email configuration.",
                    ex);
        }
    }

    @Transactional
    public void resetPassword(AdminPasswordResetRequest request) {
        resetPassword(request, User.Role.ADMIN);
    }

    @Transactional
    public void resetCustomerPassword(AdminPasswordResetRequest request) {
        resetPassword(request, User.Role.USER);
    }

    private void resetPassword(AdminPasswordResetRequest request, User.Role requiredRole) {
        validatePasswordSize(request.getPassword());
        String tokenHash = hashToken(request.getToken());
        AdminPasswordResetToken resetToken = resetTokenRepository
                .findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new BadRequestException(
                        "This password reset link is invalid or has expired"));
        if (!resetToken.getExpiresAt().isAfter(LocalDateTime.now())) {
            resetTokenRepository.delete(resetToken);
            throw new BadRequestException("This password reset link is invalid or has expired");
        }
        if (resetToken.getUser().getRole() != requiredRole) {
            throw new BadRequestException("This password reset link is invalid or has expired");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);
        resetTokenRepository.deleteByUserId(user.getId());
        log.info("Password reset completed for {} account.", requiredRole.name().toLowerCase());
    }

    private void ensureSmtpConfigured() {
        if (!StringUtils.hasText(smtpHost) || !StringUtils.hasText(smtpUsername)
                || !StringUtils.hasText(fromAddress)) {
            throw new ExternalServiceUnavailableException(
                    "Password reset email is not configured. Set SMTP settings in backend/.env.",
                    new IllegalStateException("SMTP host, username, and sender address are required."));
        }
    }

    private String createToken() {
        byte[] tokenBytes = new byte[RESET_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private String nameFromEmail(String email) {
        String localPart = email.substring(0, email.indexOf('@')).replaceAll("[._-]+", " ").trim();
        return localPart.isBlank() ? "Administrator" : localPart;
    }

    private void validatePasswordSize(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadRequestException("Password must not exceed 72 UTF-8 bytes");
        }
    }

    private AdminAccountDto toDto(User user) {
        return AdminAccountDto.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .build();
    }
}
