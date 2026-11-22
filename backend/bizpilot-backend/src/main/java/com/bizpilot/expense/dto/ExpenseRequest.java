package com.bizpilot.expense.dto;

import com.bizpilot.expense.entity.ExpenseCategory;
import com.bizpilot.payment.entity.PaymentMethod;
import jakarta.validation.constraints.*;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseRequest(
        @NotBlank @Size(max = 300) String description,
        @NotNull ExpenseCategory category,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotNull LocalDate expenseDate,
        @NotNull PaymentMethod paymentMethod,
        @Size(max = 160) String vendor,
        @Size(max = 500) String notes) {
}
