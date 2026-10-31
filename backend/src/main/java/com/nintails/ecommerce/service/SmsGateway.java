package com.nintails.ecommerce.service;

public interface SmsGateway {
    void sendOtp(String phoneNumber, String otp, String purpose);
}
