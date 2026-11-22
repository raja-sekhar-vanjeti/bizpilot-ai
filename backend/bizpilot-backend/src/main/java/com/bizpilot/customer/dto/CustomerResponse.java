package com.bizpilot.customer.dto;

import com.bizpilot.customer.entity.Customer;
import java.time.LocalDateTime;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String name,
        String email,
        String phone,
        String companyName,
        String billingAddress,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static CustomerResponse from(Customer c) {
        return new CustomerResponse(
                c.getId(),
                c.getName(),
                c.getEmail(),
                c.getPhone(),
                c.getCompanyName(),
                c.getBillingAddress(),
                c.getNotes(),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }
}
