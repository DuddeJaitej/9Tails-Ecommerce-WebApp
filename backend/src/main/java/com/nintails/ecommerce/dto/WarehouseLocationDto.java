package com.nintails.ecommerce.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WarehouseLocationDto {
    Long id;
    String name;
    String street;
    String city;
    String state;
    String postalCode;
    Double latitude;
    Double longitude;
    boolean active;
    Long operatorId;
    String operatorName;
    String operatorEmail;
}
