package com.bizpilot.customer.controller;

import com.bizpilot.common.exception.UnauthorizedException;
import com.bizpilot.common.response.ApiResponse;
import com.bizpilot.customer.dto.CustomerRequest;
import com.bizpilot.customer.dto.CustomerResponse;
import com.bizpilot.customer.service.CustomerService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CustomerResponse>> create(
            Authentication authentication,
            @Valid @RequestBody CustomerRequest request) {
        UUID ownerId = currentUserId(authentication);
        CustomerResponse response = customerService.create(ownerId, request);
        return ResponseEntity.status(201).body(ApiResponse.ok("Customer created", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<CustomerResponse>>> list(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        UUID ownerId = currentUserId(authentication);
        Page<CustomerResponse> page = customerService.list(ownerId, search, pageable);
        return ResponseEntity.ok(ApiResponse.ok("Customers loaded", page));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerResponse>> get(
            Authentication authentication,
            @PathVariable UUID id) {
        UUID ownerId = currentUserId(authentication);
        CustomerResponse response = customerService.get(ownerId, id);
        return ResponseEntity.ok(ApiResponse.ok("Customer loaded", response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerResponse>> update(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody CustomerRequest request) {
        UUID ownerId = currentUserId(authentication);
        CustomerResponse response = customerService.update(ownerId, id, request);
        return ResponseEntity.ok(ApiResponse.ok("Customer updated", response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            Authentication authentication,
            @PathVariable UUID id) {
        UUID ownerId = currentUserId(authentication);
        customerService.delete(ownerId, id);
        return ResponseEntity.noContent().build();
    }

    private UUID currentUserId(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof UUID userId)) {
            throw new UnauthorizedException("Authentication required");
        }
        return userId;
    }
}
