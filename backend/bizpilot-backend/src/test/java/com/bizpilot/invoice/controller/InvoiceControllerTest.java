package com.bizpilot.invoice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bizpilot.common.exception.ConflictException;
import com.bizpilot.common.exception.NotFoundException;
import com.bizpilot.invoice.dto.InvoiceRequest;
import com.bizpilot.invoice.dto.InvoiceResponse;
import com.bizpilot.invoice.entity.InvoiceStatus;
import com.bizpilot.invoice.service.InvoiceService;
import com.bizpilot.support.ControllerTestSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(InvoiceController.class)
class InvoiceControllerTest extends ControllerTestSupport {

    @MockitoBean
    private InvoiceService invoiceService;

    private final UUID invoiceId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();

    private InvoiceResponse response(String status) {
        return new InvoiceResponse(invoiceId, "INV-ABC", customerId, "Acme", LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31), new BigDecimal("1000.00"), new BigDecimal("18.00"),
                new BigDecimal("180.00"), new BigDecimal("0.00"), new BigDecimal("1180.00"), new BigDecimal("0.00"),
                new BigDecimal("1180.00"), "INR", null, status, null,
                List.of(new InvoiceResponse.ItemResponse(UUID.randomUUID(), "Design", new BigDecimal("1.00"),
                        new BigDecimal("1000.00"), new BigDecimal("1000.00"))));
    }

    private String validBody() {
        return "{\"customerId\":\"" + customerId + "\",\"invoiceDate\":\"2026-03-01\",\"dueDate\":\"2026-03-31\","
                + "\"taxPercent\":18,\"items\":[{\"description\":\"Design\",\"quantity\":1,\"unitPrice\":1000}]}";
    }

    @Test
    void createReturns201WithInvoiceDto() throws Exception {
        when(invoiceService.create(eq(ownerId), any(InvoiceRequest.class))).thenReturn(response("DRAFT"));

        mockMvc.perform(post("/api/invoices").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.invoiceNumber").value("INV-ABC"))
                .andExpect(jsonPath("$.data.totalAmount").value(1180.00))
                .andExpect(jsonPath("$.data.items[0].lineTotal").value(1000.00))
                .andExpect(jsonPath("$.data.owner").doesNotExist())
                .andExpect(jsonPath("$.data.customer").doesNotExist());
    }

    @Test
    void createWithMissingFieldsIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/invoices").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invoiceDate\":\"2026-03-01\",\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(post("/api/invoices").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody().replace("\"quantity\":1", "\"quantity\":0")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(invoiceService);
    }

    @Test
    void getMissingInvoiceIsNotFound() throws Exception {
        when(invoiceService.get(ownerId, invoiceId)).thenThrow(new NotFoundException("Invoice not found"));

        mockMvc.perform(get("/api/invoices/{id}", invoiceId).header("Authorization", bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Invoice not found"));
    }

    @Test
    void getWithMalformedIdIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/invoices/not-a-uuid").header("Authorization", bearer()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(invoiceService);
    }

    @Test
    void listReturnsPageScopedToToken() throws Exception {
        when(invoiceService.list(eq(ownerId), eq(InvoiceStatus.SENT), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response("SENT"))));

        mockMvc.perform(get("/api/invoices").param("status", "SENT").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].status").value("SENT"));
    }

    @Test
    void listWithUnknownStatusIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/invoices").param("status", "WHATEVER").header("Authorization", bearer()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(invoiceService);
    }

    @Test
    void sendingNonDraftIsConflict() throws Exception {
        when(invoiceService.markSent(ownerId, invoiceId)).thenThrow(new ConflictException("Only draft invoices can be sent"));

        mockMvc.perform(post("/api/invoices/{id}/send", invoiceId).header("Authorization", bearer()))
                .andExpect(status().isConflict());
    }

    @Test
    void updateAndCancelUseTokenOwner() throws Exception {
        when(invoiceService.update(eq(ownerId), eq(invoiceId), any())).thenReturn(response("DRAFT"));
        when(invoiceService.cancel(ownerId, invoiceId)).thenReturn(response("CANCELLED"));

        mockMvc.perform(put("/api/invoices/{id}", invoiceId).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/invoices/{id}/cancel", invoiceId).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        verify(invoiceService).cancel(ownerId, invoiceId);
    }

    @Test
    void anotherBusinessInvoiceIsNotFound() throws Exception {
        UUID otherBusiness = UUID.randomUUID();
        when(invoiceService.get(otherBusiness, invoiceId)).thenThrow(new NotFoundException("Invoice not found"));

        mockMvc.perform(get("/api/invoices/{id}", invoiceId).header("Authorization", bearer(otherBusiness)))
                .andExpect(status().isNotFound());
    }

    @Test
    void requestsWithoutTokenAreUnauthorized() throws Exception {
        mockMvc.perform(get("/api/invoices")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/invoices").contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(invoiceService);
    }
}
