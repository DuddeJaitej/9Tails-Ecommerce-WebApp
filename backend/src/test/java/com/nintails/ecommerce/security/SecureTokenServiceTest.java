package com.nintails.ecommerce.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SecureTokenServiceTest {

    private SecureTokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new SecureTokenService();
        ReflectionTestUtils.setField(tokenService, "secret",
                "test-secret-with-more-than-thirty-two-characters");
        tokenService.initialize();
    }

    @Test
    void qrTokensAreUniqueHighEntropyAndOnlyHashMatchesPersistedValue() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            String token = tokenService.newQrToken();
            assertEquals(43, token.length());
            assertTrue(tokens.add(token));
            assertEquals(64, tokenService.hashToken(token).length());
            assertNotEquals(token, tokenService.hashToken(token));
        }
    }

    @Test
    void encryptedTokenCanBeReturnedToTheAuthorizedQrRenderer() {
        String rawToken = tokenService.newQrToken();
        assertEquals(rawToken, tokenService.decrypt(tokenService.encrypt(rawToken)));
    }

    @Test
    void otpComparisonIsConstantFormatAndRejectsIncorrectValues() {
        String otp = tokenService.newOtp();
        assertTrue(otp.matches("[0-9]{6}"));
        assertTrue(tokenService.matchesOtp(otp, tokenService.hashOtp(otp)));
        assertFalse(tokenService.matchesOtp("000000", tokenService.hashOtp(otp)));
    }
}
