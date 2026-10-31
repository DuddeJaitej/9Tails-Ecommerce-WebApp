package com.nintails.ecommerce.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtUtilsConfigurationTest {

    @Test
    void acceptsSigningSecretsWithAtLeast32Utf8Bytes() {
        JwtUtils jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", "a".repeat(32));

        assertDoesNotThrow(jwtUtils::validateJwtSecret);
    }

    @Test
    void rejectsSigningSecretsShorterThan32Utf8BytesWithActionableMessage() {
        JwtUtils jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", "short-secret");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class, jwtUtils::validateJwtSecret);

        assertEquals(
                "Invalid JWT signing secret: configure JWT_SECRET with at least 32 UTF-8 bytes.",
                exception.getMessage());
    }
}
