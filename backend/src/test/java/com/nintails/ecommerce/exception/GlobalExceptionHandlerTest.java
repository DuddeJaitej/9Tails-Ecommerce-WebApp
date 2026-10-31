package com.nintails.ecommerce.exception;

import com.nintails.ecommerce.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void authenticationFailuresReturnActionableUnauthorizedResponse() {
        var response = handler.handleAuthentication(
                new InternalAuthenticationServiceException("Authentication failed"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Sign-in failed. Check your credentials or use Continue with Google.",
                response.getBody().getMessage());
    }

    @Test
    void googleProviderFailuresReturnServiceUnavailable() {
        var response = handler.handleExternalServiceUnavailable(
                new ExternalServiceUnavailableException(
                        "Google sign-in is temporarily unavailable. Please try again.",
                        new ResourceAccessException("Google endpoint unavailable")));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        ApiResponse<Void> body = response.getBody();
        assertEquals("Google sign-in is temporarily unavailable. Please try again.",
                body.getMessage());
    }

    @Test
    void missingApiRoutesReturnNotFoundInsteadOfInternalServerError() {
        var response = handler.handleMissingRoute(
                new NoResourceFoundException(HttpMethod.PATCH, "/owner/fulfillments/31/pickup-location"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("The requested API route was not found. Restart the backend if it was recently updated.",
                response.getBody().getMessage());
    }
}
