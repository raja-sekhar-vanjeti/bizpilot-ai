package com.bizpilot.expense.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bizpilot.auth.entity.User;
import com.bizpilot.auth.repository.UserRepository;
import com.bizpilot.common.exception.ConflictException;
import com.bizpilot.common.exception.NotFoundException;
import com.bizpilot.expense.dto.ExpenseRequest;
import com.bizpilot.expense.entity.Expense;
import com.bizpilot.expense.repository.ExpenseRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private UserRepository userRepository;

    private ExpenseService expenseService;

    private UUID ownerId;
    private UUID otherOwnerId;
    private UUID expenseId;

    @BeforeEach
    void setUp() {
        expenseService = new ExpenseService(expenseRepository, userRepository);
        ownerId = UUID.randomUUID();
        otherOwnerId = UUID.randomUUID();
        expenseId = UUID.randomUUID();
    }

    private static ExpenseRequest request(BigDecimal amount) {
        return new ExpenseRequest("Office supplies", null, amount, LocalDate.of(2024, 1, 15),
                null, "Staples", "Q1 restock");
    }

    private User user(UUID id) {
        User user = new User();
        user.setId(id);
        user.setFullName("Owner Person");
        user.setBusinessName("Owner Biz");
        user.setEmail("owner@example.com");
        return user;
    }

    private Expense expense(User owner) {
        Expense expense = new Expense();
        expense.setOwner(owner);
        expense.setDescription("Office supplies");
        expense.setAmount(new BigDecimal("50.00"));
        expense.setExpenseDate(LocalDate.of(2024, 1, 15));
        return expense;
    }

    @Test
    void createSavesExpenseOwnedByLoadedUserWithScaledAmount() {
        User owner = user(ownerId);
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

        expenseService.create(ownerId, request(new BigDecimal("1200.5")));

        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).save(captor.capture());
        Expense saved = captor.getValue();
        assertEquals(0, saved.getAmount().compareTo(new BigDecimal("1200.50")));
        assertEquals(owner, saved.getOwner());
    }

    @Test
    void createWithUnknownOwnerThrowsNotFoundAndNeverSaves() {
        when(userRepository.findById(ownerId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> expenseService.create(ownerId, request(new BigDecimal("10.00"))));

        verify(expenseRepository, never()).save(any());
    }

    @Test
    void getExpenseOwnedByAnotherBusinessThrowsNotFoundAndUsesCallerOwnerId() {
        // Expense belongs to otherOwnerId; the caller is ownerId, so the scoped lookup finds nothing.
        when(expenseRepository.findByIdAndOwnerId(expenseId, ownerId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> expenseService.get(ownerId, expenseId));

        verify(expenseRepository).findByIdAndOwnerId(expenseId, ownerId);
        verify(expenseRepository, never()).findByIdAndOwnerId(expenseId, otherOwnerId);
    }

    @Test
    void listWithFromAfterToThrowsConflictAndNeverQueries() {
        LocalDate from = LocalDate.of(2024, 3, 1);
        LocalDate to = LocalDate.of(2024, 2, 1);

        assertThrows(ConflictException.class,
                () -> expenseService.list(ownerId, null, from, to, PageRequest.of(0, 20)));

        verifyNoInteractions(expenseRepository);
    }

    @Test
    void deleteUnownedExpenseThrowsNotFoundAndNeverDeletes() {
        when(expenseRepository.findByIdAndOwnerId(expenseId, ownerId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> expenseService.delete(ownerId, expenseId));

        verify(expenseRepository, never()).delete(any());
    }

    @Test
    void deleteOwnedExpenseCallsRepositoryDeleteOnce() {
        Expense owned = expense(user(ownerId));
        when(expenseRepository.findByIdAndOwnerId(expenseId, ownerId)).thenReturn(Optional.of(owned));

        expenseService.delete(ownerId, expenseId);

        verify(expenseRepository, times(1)).delete(owned);
    }
}
