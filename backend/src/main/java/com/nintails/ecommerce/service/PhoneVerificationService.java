package com.nintails.ecommerce.service;

import com.nintails.ecommerce.dto.PhoneVerificationChallengeDto;
import com.nintails.ecommerce.entity.PhoneVerificationChallenge;
import com.nintails.ecommerce.entity.User;
import com.nintails.ecommerce.exception.BadRequestException;
import com.nintails.ecommerce.exception.ResourceNotFoundException;
import com.nintails.ecommerce.repository.PhoneVerificationChallengeRepository;
import com.nintails.ecommerce.repository.UserRepository;
import com.nintails.ecommerce.security.SecureTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PhoneVerificationService {

    private final UserRepository userRepository;
    private final PhoneVerificationChallengeRepository challengeRepository;
    private final SecureTokenService secureTokenService;
    private final SmsGateway smsGateway;

    @Transactional
    public PhoneVerificationChallengeDto requestVerification(String email, String phoneNumber) {
        User user = getUser(email);
        if (userRepository.existsByPhoneNumberAndPhoneVerifiedTrue(phoneNumber)
                && !Objects.equals(user.getPhoneNumber(), phoneNumber)) {
            throw new BadRequestException("This phone number is already verified for another account.");
        }
        LocalDateTime since = LocalDateTime.now().minusMinutes(15);
        if (challengeRepository.countByUserIdAndCreatedAtAfter(user.getId(), since) >= 3) {
            throw new BadRequestException("Too many verification codes requested. Try again later.");
        }

        String code = secureTokenService.newOtp();
        PhoneVerificationChallenge challenge = challengeRepository.save(
                PhoneVerificationChallenge.builder()
                        .user(user)
                        .phoneNumber(phoneNumber)
                        .codeHash(secureTokenService.hashOtp(code))
                        .expiresAt(LocalDateTime.now().plusMinutes(10))
                        .build());
        smsGateway.sendOtp(phoneNumber, code, "phone verification");

        return PhoneVerificationChallengeDto.builder()
                .challengeId(challenge.getId())
                .maskedPhone("******" + phoneNumber.substring(phoneNumber.length() - 4))
                .expiresAt(challenge.getExpiresAt())
                .build();
    }

    @Transactional(noRollbackFor = BadRequestException.class)
    public void verify(String email, Long challengeId, String code) {
        User user = getUser(email);
        PhoneVerificationChallenge challenge = challengeRepository.findByIdForUpdate(challengeId)
                .filter(item -> item.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Phone verification challenge", challengeId));
        if (challenge.getConsumedAt() != null) {
            throw new BadRequestException("This verification code has already been used.");
        }
        if (challenge.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("This verification code has expired. Request a new code.");
        }
        if (challenge.getFailedAttempts() >= 5) {
            throw new BadRequestException("Too many incorrect codes. Request a new code.");
        }
        if (!secureTokenService.matchesOtp(code, challenge.getCodeHash())) {
            challenge.setFailedAttempts(challenge.getFailedAttempts() + 1);
            throw new BadRequestException("The verification code is incorrect.");
        }
        challenge.setConsumedAt(LocalDateTime.now());
        user.setPhoneNumber(challenge.getPhoneNumber());
        user.setPhoneVerified(true);
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
