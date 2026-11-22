package com.bizpilot.invoice.controller;

import com.bizpilot.common.exception.UnauthorizedException;
import com.bizpilot.common.response.ApiResponse;
import com.bizpilot.invoice.dto.InvoiceRequest;
import com.bizpilot.invoice.dto.InvoiceResponse;
import com.bizpilot.invoice.entity.InvoiceStatus;
import com.bizpilot.invoice.service.InvoiceService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<InvoiceResponse>> create(
            Authentication authentication,
            @Valid @RequestBody InvoiceRequest request) {
        UUID ownerId = currentUserId(authentication);
        InvoiceResponse response = invoiceService.create(ownerId, request);
        return ResponseEntity.status(201).body(ApiResponse.ok("Invoice created", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<InvoiceResponse>>> list(
            Authentication authentication,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @PageableDefault(size = 20, sort = "invoiceDate", direction = Sort.Direction.DESC) Pageable pageable) {
        UUID ownerId = currentUserId(authentication);
        Page<InvoiceResponse> page = invoiceService.list(ownerId, status, customerId, from, to, pageable);
        return ResponseEntity.ok(ApiResponse.ok("Invoices loaded", page));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InvoiceResponse>> get(
            Authentication authentication,
            @PathVariable UUID id) {
        UUID ownerId = currentUserId(authentication);
        InvoiceResponse response = invoiceService.get(ownerId, id);
        return ResponseEntity.ok(ApiResponse.ok("Invoice loaded", response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<InvoiceResponse>> update(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody InvoiceRequest request) {
        UUID ownerId = currentUserId(authentication);
        InvoiceResponse response = invoiceService.update(ownerId, id, request);
        return ResponseEntity.ok(ApiResponse.ok("Invoice updated", response));
    }

    @PostMapping("/{id}/send")
    public ResponseEntity<ApiResponse<InvoiceResponse>> send(
            Authentication authentication,
            @PathVariable UUID id) {
        UUID ownerId = currentUserId(authentication);
        InvoiceResponse response = invoiceService.markSent(ownerId, id);
        return ResponseEntity.ok(ApiResponse.ok("Invoice sent", response));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<InvoiceResponse>> cancel(
            Authentication authentication,
            @PathVariable UUID id) {
        UUID ownerId = currentUserId(authentication);
        InvoiceResponse response = invoiceService.cancel(ownerId, id);
        return ResponseEntity.ok(ApiResponse.ok("Invoice cancelled", response));
    }

    private UUID currentUserId(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof UUID userId)) {
            throw new UnauthorizedException("Authentication required");
        }
        return userId;
    }
}
