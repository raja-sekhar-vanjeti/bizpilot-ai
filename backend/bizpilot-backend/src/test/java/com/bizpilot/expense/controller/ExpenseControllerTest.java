package com.bizpilot.expense.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bizpilot.common.exception.ConflictException;
import com.bizpilot.common.exception.NotFoundException;
import com.bizpilot.expense.dto.ExpenseRequest;
import com.bizpilot.expense.entity.Expense;
import com.bizpilot.expense.entity.ExpenseCategory;
import com.bizpilot.expense.service.ExpenseService;
import com.bizpilot.support.ControllerTestSupport;
import com.bizpilot.support.TestData;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(ExpenseController.class)
class ExpenseControllerTest extends ControllerTestSupport {

    @MockitoBean
    private ExpenseService expenseService;

    private static final String BODY = "{\"description\":\"Hosting\",\"category\":\"SOFTWARE\",\"amount\":1500.50,"
            + "\"expenseDate\":\"2026-03-10\",\"paymentMethod\":\"CARD\",\"vendor\":\"Cloud Co\"}";

    private Expense expense() {
        Expense e = TestData.expense(TestData.user(ownerId), "Hosting", "1500.50", LocalDate.of(2026, 3, 10));
        e.setCategory(ExpenseCategory.SOFTWARE);
        return e;
    }

    @Test
    void createReturns201WithoutOwnerDetails() throws Exception {
        Expense e = expense();
        when(expenseService.create(eq(ownerId), any(ExpenseRequest.class))).thenReturn(e);

        mockMvc.perform(post("/api/expenses").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(e.getId().toString()))
                .andExpect(jsonPath("$.data.category").value("SOFTWARE"))
                .andExpect(jsonPath("$.data.amount").value(1500.50))
                .andExpect(jsonPath("$.data.owner").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void ownerIdInBodyIsIgnored() throws Exception {
        when(expenseService.create(eq(ownerId), any(ExpenseRequest.class))).thenReturn(expense());

        mockMvc.perform(post("/api/expenses").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("{", "{\"ownerId\":\"" + UUID.randomUUID() + "\",")))
                .andExpect(status().isCreated());
        verify(expenseService).create(eq(ownerId), any(ExpenseRequest.class));
    }

    @Test
    void invalidExpenseIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/expenses").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY.replace("1500.50", "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("amount")));
        mockMvc.perform(post("/api/expenses").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY.replace("SOFTWARE", "FOOD")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(expenseService);
    }

    @Test
    void listFiltersByCategory() throws Exception {
        when(expenseService.list(eq(ownerId), eq(ExpenseCategory.SOFTWARE), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(expense())));

        mockMvc.perform(get("/api/expenses").param("category", "SOFTWARE").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].description").value("Hosting"));
    }

    @Test
    void invertedDateRangeIsConflict() throws Exception {
        when(expenseService.list(eq(ownerId), isNull(), any(), any(), any()))
                .thenThrow(new ConflictException("Date range end cannot be before start"));

        mockMvc.perform(get("/api/expenses").param("from", "2026-03-10").param("to", "2026-03-01")
                        .header("Authorization", bearer()))
                .andExpect(status().isConflict());
    }

    @Test
    void missingExpenseIsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(expenseService.get(ownerId, id)).thenThrow(new NotFoundException("Expense not found"));
        when(expenseService.update(eq(ownerId), eq(id), any())).thenThrow(new NotFoundException("Expense not found"));

        mockMvc.perform(get("/api/expenses/{id}", id).header("Authorization", bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/expenses/{id}", id).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturns204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/expenses/{id}", id).header("Authorization", bearer()))
                .andExpect(status().isNoContent());
        verify(expenseService).delete(ownerId, id);
    }

    @Test
    void requestsWithoutTokenAreUnauthorized() throws Exception {
        mockMvc.perform(get("/api/expenses")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/expenses/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
        verifyNoInteractions(expenseService);
    }
}
