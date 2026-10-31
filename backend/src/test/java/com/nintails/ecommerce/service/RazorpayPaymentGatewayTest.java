package com.nintails.ecommerce.service;

import com.nintails.ecommerce.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RazorpayPaymentGatewayTest {

    private static final String KEY_SECRET = "test-key-secret-for-signature-checks";

    @Test
    void acceptsValidRazorpayHmacSignature() throws Exception {
        RazorpayPaymentGateway gateway = configuredGateway();
        String signature = signature("order_test_123", "pay_test_456");

        assertDoesNotThrow(() ->
                gateway.verifySignature("order_test_123", "pay_test_456", signature));
    }

    @Test
    void rejectsTamperedRazorpayHmacSignature() {
        RazorpayPaymentGateway gateway = configuredGateway();

        assertThrows(BadRequestException.class, () ->
                gateway.verifySignature("order_test_123", "pay_test_456", "00".repeat(32)));
    }

    @Test
    void rejectsOrderAmountsBelowTheRazorpayMinimum() {
        RazorpayPaymentGateway gateway = configuredGateway();

        assertThrows(BadRequestException.class, () -> gateway.createOrder(99, "receipt"));
    }

    private RazorpayPaymentGateway configuredGateway() {
        RazorpayPaymentGateway gateway = new RazorpayPaymentGateway();
        ReflectionTestUtils.setField(gateway, "keyId", "rzp_test_example");
        ReflectionTestUtils.setField(gateway, "keySecret", KEY_SECRET);
        return gateway;
    }

    private String signature(String orderId, String paymentId) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(KEY_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(
                (orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8)));
    }
}
