package com.bizpilot.payment.controller;

import com.bizpilot.common.exception.UnauthorizedException;
import com.bizpilot.common.response.ApiResponse;
import com.bizpilot.payment.entity.Payment;
import com.bizpilot.payment.dto.PaymentResponse;
import com.bizpilot.payment.entity.PaymentMethod;
import com.bizpilot.payment.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/payments")
    public ResponseEntity<ApiResponse<PaymentResponse>> record(
            Authentication authentication,
            @Valid @RequestBody PaymentRequest request) {
        UUID ownerId = currentUserId(authentication);
        Payment response = paymentService.record(
                ownerId,
                request.invoiceId(),
                request.amount(),
                request.paymentDate(),
                request.method(),
                request.reference(),
                request.notes());
        return ResponseEntity.status(201).body(ApiResponse.ok("Payment recorded", PaymentResponse.from(response)));
    }

    @GetMapping("/payments/{id}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getById(Authentication authentication, @PathVariable UUID id) {
        UUID ownerId = currentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok("Payment loaded", PaymentResponse.from(paymentService.getById(ownerId, id))));
    }

    @GetMapping("/payments")
    public ResponseEntity<ApiResponse<Page<PaymentResponse>>> list(Authentication authentication,
            @PageableDefault(size = 20, sort = "paymentDate", direction = Sort.Direction.DESC) Pageable pageable) {
        UUID ownerId = currentUserId(authentication);
        Page<PaymentResponse> page = paymentService.list(ownerId, pageable).map(PaymentResponse::from);
        return ResponseEntity.ok(ApiResponse.ok("Payments loaded", page));
    }

    @GetMapping("/invoices/{invoiceId}/payments")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> listForInvoice(
            Authentication authentication,
            @PathVariable UUID invoiceId) {
        UUID ownerId = currentUserId(authentication);
        List<PaymentResponse> payments = paymentService.listForInvoice(ownerId, invoiceId).stream()
                .map(PaymentResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok("Payments loaded", payments));
    }

    public record PaymentRequest(
            @NotNull UUID invoiceId,
            @NotNull BigDecimal amount,
            LocalDate paymentDate,
            @NotNull PaymentMethod method,
            String reference,
            String notes) {
    }

    private UUID currentUserId(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof UUID userId)) {
            throw new UnauthorizedException("Authentication required");
        }
        return userId;
    }
}
