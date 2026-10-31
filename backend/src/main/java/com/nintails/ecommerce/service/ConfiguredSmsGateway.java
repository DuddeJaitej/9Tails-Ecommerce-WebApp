package com.nintails.ecommerce.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nintails.ecommerce.exception.ExternalServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfiguredSmsGateway implements SmsGateway {

    private final ObjectMapper objectMapper;
    private final Environment environment;

    @Value("${app.sms.provider:disabled}")
    private String provider;

    @Value("${app.sms.endpoint:}")
    private String endpoint;

    @Value("${app.sms.api-key:}")
    private String apiKey;

    @Value("${app.sms.sender:9TAILS}")
    private String sender;

    @Override
    public void sendOtp(String phoneNumber, String otp, String purpose) {
        if ("mock".equalsIgnoreCase(provider)) {
            if (!environment.acceptsProfiles(Profiles.of("dev", "test"))) {
                throw new ExternalServiceUnavailableException(
                        "The mock SMS provider is permitted only in dev or test profiles.", null);
            }
            log.info("Mock SMS provider accepted a {} OTP for a verified number ending {} (code suppressed).",
                    purpose, phoneNumber.substring(Math.max(0, phoneNumber.length() - 2)));
            return;
        }
        if (!"http-json".equalsIgnoreCase(provider) || endpoint.isBlank() || apiKey.isBlank()) {
            throw new ExternalServiceUnavailableException(
                    "SMS delivery is not configured. Configure SMS_PROVIDER=http-json, SMS_ENDPOINT, and SMS_API_KEY.",
                    null);
        }

        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "to", phoneNumber,
                    "sender", sender,
                    "message", "Your 9Tails " + purpose + " code is " + otp));
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<Void> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build()
                    .send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ExternalServiceUnavailableException(
                        "The SMS provider did not accept the OTP message.", null);
            }
        } catch (ExternalServiceUnavailableException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ExternalServiceUnavailableException(
                    "The SMS provider is unavailable. Please try again later.", exception);
        }
    }
}
