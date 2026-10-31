package com.nintails.ecommerce.exception;

public class ExpiredResourceException extends RuntimeException {
    public ExpiredResourceException(String message) {
        super(message);
    }
}
