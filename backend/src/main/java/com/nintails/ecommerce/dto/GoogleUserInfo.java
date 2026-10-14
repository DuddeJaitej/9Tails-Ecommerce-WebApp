package com.nintails.ecommerce.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class GoogleUserInfo {

    private String name;
    private String email;
    private String picture;

    @JsonProperty("email_verified")
    private Boolean emailVerified;
}
