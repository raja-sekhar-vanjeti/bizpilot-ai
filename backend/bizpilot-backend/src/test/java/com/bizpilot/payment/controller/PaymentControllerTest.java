package com.bizpilot.payment.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bizpilot.common.exception.NotFoundException;
import com.bizpilot.common.exception.UnauthorizedException;
import com.bizpilot.common.response.ApiResponse;
import com.bizpilot.invoice.entity.Invoice;
import com.bizpilot.payment.dto.PaymentResponse;
import com.bizpilot.payment.entity.Payment;
import com.bizpilot.payment.entity.PaymentMethod;
import com.bizpilot.payment.service.PaymentService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    @Mock
    private Authentication authentication;

    private PaymentController controller;

    private UUID ownerId;
    private UUID paymentId;
    private UUID invoiceId;

    @BeforeEach
    void setUp() {
        controller = new PaymentController(paymentService);
        ownerId = UUID.randomUUID();
        paymentId = UUID.randomUUID();
        invoiceId = UUID.randomUUID();
    }

    private void authenticatedAs(Object principal) {
        lenient().when(authentication.getPrincipal()).thenReturn(principal);
    }

    private Payment payment() {
        Invoice invoice = new Invoice();
        invoice.setId(invoiceId);

        Payment payment = new Payment();
        payment.setId(paymentId);
        payment.setInvoice(invoice);
        payment.setAmount(new BigDecimal("10000.00"));
        payment.setPaymentDate(LocalDate.of(2025, 1, 15));
        payment.setMethod(PaymentMethod.BANK_TRANSFER);
        return payment;
    }

    @Test
    void getByIdReturnsPaymentResponseWithStatus200() {
        authenticatedAs(ownerId);
        when(paymentService.getById(ownerId, paymentId)).thenReturn(payment());

        ResponseEntity<ApiResponse<PaymentResponse>> response = controller.getById(authentication, paymentId);

        assertEquals(200, response.getStatusCode().value());
        ApiResponse<PaymentResponse> body = response.getBody();
        assertEquals(true, body.success());
        PaymentResponse data = assertInstanceOf(PaymentResponse.class, body.data());
        assertEquals(paymentId, data.id());
        assertEquals(new BigDecimal("10000.00"), data.amount());
        assertEquals("BANK_TRANSFER", data.method());
    }

    @Test
    void getByIdMissingPaymentThrowsNotFound() {
        authenticatedAs(ownerId);
        when(paymentService.getById(ownerId, paymentId))
                .thenThrow(new NotFoundException("Payment not found"));

        assertThrows(NotFoundException.class, () -> controller.getById(authentication, paymentId));
    }

    @Test
    void getByIdWithNonUuidPrincipalThrowsUnauthorizedAndNeverCallsService() {
        authenticatedAs("anonymous");

        assertThrows(UnauthorizedException.class, () -> controller.getById(authentication, paymentId));

        verifyNoInteractions(paymentService);
    }

    @Test
    void listReturnsPagedPaymentResponses() {
        authenticatedAs(ownerId);
        Pageable pageable = PageRequest.of(0, 20);
        when(paymentService.list(ownerId, pageable)).thenReturn(new PageImpl<>(List.of(payment())));

        ResponseEntity<ApiResponse<Page<PaymentResponse>>> response = controller.list(authentication, pageable);

        assertEquals(200, response.getStatusCode().value());
        Page<PaymentResponse> page = response.getBody().data();
        PaymentResponse first = assertInstanceOf(PaymentResponse.class, page.getContent().get(0));
        assertEquals(paymentId, first.id());
    }

    @Test
    void listWithNonUuidPrincipalThrowsUnauthorizedAndNeverCallsService() {
        authenticatedAs("anonymous");

        assertThrows(UnauthorizedException.class,
                () -> controller.list(authentication, PageRequest.of(0, 20)));

        verifyNoInteractions(paymentService);
    }

    @Test
    void recordReturnsCreatedWithPaymentResponse() {
        authenticatedAs(ownerId);
        LocalDate paymentDate = LocalDate.of(2025, 1, 15);
        when(paymentService.record(eq(ownerId), eq(invoiceId), eq(new BigDecimal("10000.00")),
                eq(paymentDate), eq(PaymentMethod.BANK_TRANSFER), eq("REF-1"), eq("first instalment")))
                .thenReturn(payment());

        PaymentController.PaymentRequest request = new PaymentController.PaymentRequest(
                invoiceId, new BigDecimal("10000.00"), paymentDate, PaymentMethod.BANK_TRANSFER,
                "REF-1", "first instalment");

        ResponseEntity<ApiResponse<PaymentResponse>> response = controller.record(authentication, request);

        assertEquals(201, response.getStatusCode().value());
        PaymentResponse data = assertInstanceOf(PaymentResponse.class, response.getBody().data());
        assertEquals(paymentId, data.id());
        assertEquals(invoiceId, data.invoiceId());
        assertEquals("BANK_TRANSFER", data.method());
    }

    @Test
    void paymentResponseDoesNotExposeOwnerOrUser() {
        List<String> componentNames = Arrays.stream(PaymentResponse.class.getRecordComponents())
                .map(component -> component.getName())
                .toList();

        assertEquals(false, componentNames.contains("owner"));
        assertEquals(false, componentNames.contains("user"));
    }
}
