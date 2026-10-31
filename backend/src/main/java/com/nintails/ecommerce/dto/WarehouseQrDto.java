package com.nintails.ecommerce.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WarehouseQrDto {
    String warehouseName;
    String warehouseAddress;
    String token;
    boolean refreshed;
}
