package com.bizpilot.expense.dto;

import com.bizpilot.expense.entity.Expense;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ExpenseResponse(
        UUID id,
        String description,
        String category,
        BigDecimal amount,
        LocalDate expenseDate,
        String paymentMethod,
        String vendor,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static ExpenseResponse from(Expense e) {
        return new ExpenseResponse(
                e.getId(),
                e.getDescription(),
                e.getCategory().name(),
                e.getAmount(),
                e.getExpenseDate(),
                e.getPaymentMethod().name(),
                e.getVendor(),
                e.getNotes(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }
}
