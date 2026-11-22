package com.bizpilot.expense.controller;

import com.bizpilot.common.exception.UnauthorizedException;
import com.bizpilot.common.response.ApiResponse;
import com.bizpilot.expense.dto.ExpenseRequest;
import com.bizpilot.expense.dto.ExpenseResponse;
import com.bizpilot.expense.entity.Expense;
import com.bizpilot.expense.entity.ExpenseCategory;
import com.bizpilot.expense.service.ExpenseService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ExpenseResponse>> create(
            Authentication authentication,
            @Valid @RequestBody ExpenseRequest request) {
        UUID ownerId = currentUserId(authentication);
        Expense created = expenseService.create(ownerId, request);
        return ResponseEntity.status(201).body(ApiResponse.ok("Expense created", ExpenseResponse.from(created)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ExpenseResponse>>> list(
            Authentication authentication,
            @RequestParam(required = false) ExpenseCategory category,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @PageableDefault(size = 20, sort = "expenseDate", direction = Sort.Direction.DESC) Pageable pageable) {
        UUID ownerId = currentUserId(authentication);
        Page<ExpenseResponse> page = expenseService.list(ownerId, category, from, to, pageable)
                .map(ExpenseResponse::from);
        return ResponseEntity.ok(ApiResponse.ok("Expenses loaded", page));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ExpenseResponse>> get(Authentication authentication, @PathVariable UUID id) {
        UUID ownerId = currentUserId(authentication);
        Expense expense = expenseService.get(ownerId, id);
        return ResponseEntity.ok(ApiResponse.ok("Expense loaded", ExpenseResponse.from(expense)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ExpenseResponse>> update(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody ExpenseRequest request) {
        UUID ownerId = currentUserId(authentication);
        Expense updated = expenseService.update(ownerId, id, request);
        return ResponseEntity.ok(ApiResponse.ok("Expense updated", ExpenseResponse.from(updated)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable UUID id) {
        UUID ownerId = currentUserId(authentication);
        expenseService.delete(ownerId, id);
        return ResponseEntity.noContent().build();
    }

    private UUID currentUserId(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof UUID userId)) {
            throw new UnauthorizedException("Authentication required");
        }
        return userId;
    }
}
