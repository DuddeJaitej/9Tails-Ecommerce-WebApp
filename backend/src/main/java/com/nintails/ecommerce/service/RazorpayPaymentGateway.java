package com.nintails.ecommerce.service;

import com.nintails.ecommerce.exception.BadRequestException;
import com.nintails.ecommerce.exception.ExternalServiceUnavailableException;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
@Slf4j
public class RazorpayPaymentGateway {

    private static final long MINIMUM_AMOUNT_PAISE = 100;
    private static final String CURRENCY = "INR";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Value("${razorpay.key-id:}")
    private String keyId;

    @Value("${razorpay.key-secret:}")
    private String keySecret;

    public String getKeyId() {
        requireCredentials();
        return keyId;
    }

    public CreatedRazorpayOrder createOrder(long amountPaise, String receipt) {
        if (amountPaise < MINIMUM_AMOUNT_PAISE) {
            throw new BadRequestException("Payment amount must be at least ₹1.00");
        }
        requireCredentials();

        try {
            JSONObject request = new JSONObject()
                    .put("amount", amountPaise)
                    .put("currency", CURRENCY)
                    .put("receipt", receipt);
            JSONObject response = new RazorpayClient(keyId, keySecret)
                    .orders.create(request).toJson();
            return new CreatedRazorpayOrder(
                    response.getString("id"),
                    response.getLong("amount"),
                    response.getString("currency"));
        } catch (RazorpayException | org.json.JSONException ex) {
            log.error("Razorpay order creation failed", ex);
            throw new ExternalServiceUnavailableException(
                    "Online payment could not be started. Please try again.", ex);
        }
    }

    public void verifySignature(String orderId, String paymentId, String signature) {
        requireCredentials();
        if (orderId == null || orderId.isBlank()
                || paymentId == null || paymentId.isBlank()
                || signature == null || signature.isBlank()) {
            throw new BadRequestException("Razorpay payment details are incomplete");
        }

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] expected = mac.doFinal(
                    (orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8));
            byte[] provided = HexFormat.of().parseHex(signature);
            if (!MessageDigest.isEqual(expected, provided)) {
                throw new BadRequestException("Razorpay payment signature is invalid");
            }
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Razorpay payment signature is invalid");
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to verify Razorpay payment signature", ex);
        }
    }

    private void requireCredentials() {
        if (keyId == null || keyId.isBlank() || keySecret == null || keySecret.isBlank()) {
            throw new ExternalServiceUnavailableException(
                    "Razorpay is not configured. Set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET in backend/.env.",
                    new IllegalStateException("Razorpay credentials are missing"));
        }
    }

    public record CreatedRazorpayOrder(String orderId, long amount, String currency) {
    }
}
