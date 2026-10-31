package com.nintails.ecommerce.dto;

import com.nintails.ecommerce.entity.User;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserRoleUpdateRequest {
    @NotNull
    private User.Role role;
}
