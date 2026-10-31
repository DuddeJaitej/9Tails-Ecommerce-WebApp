package com.nintails.ecommerce.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class BusinessLocationDto {
    Long id;
    String name;
    String street;
    String city;
    String state;
    String postalCode;
    String country;
    String contactPhone;
    Double latitude;
    Double longitude;
    boolean verifiedByOwner;
}
