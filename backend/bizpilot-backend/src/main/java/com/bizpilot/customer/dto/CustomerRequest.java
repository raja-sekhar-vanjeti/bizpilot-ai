package com.bizpilot.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerRequest(
        @NotBlank @Size(max = 160) String name,
        @Email @Size(max = 255) String email,
        @Size(max = 20) String phone,
        @Size(max = 160) String companyName,
        @Size(max = 500) String billingAddress,
        @Size(max = 1000) String notes) {
}
