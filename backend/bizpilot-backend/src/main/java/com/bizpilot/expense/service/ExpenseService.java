package com.bizpilot.expense.service;

import com.bizpilot.audit.service.AuditService;
import com.bizpilot.auth.entity.User;
import com.bizpilot.auth.repository.UserRepository;
import com.bizpilot.common.exception.ConflictException;
import com.bizpilot.common.exception.NotFoundException;
import com.bizpilot.expense.dto.ExpenseRequest;
import com.bizpilot.expense.entity.Expense;
import com.bizpilot.expense.entity.ExpenseCategory;
import com.bizpilot.expense.repository.ExpenseRepository;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public ExpenseService(ExpenseRepository expenseRepository, UserRepository userRepository,
                          AuditService auditService) {
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public Expense create(UUID ownerId, ExpenseRequest req) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        Expense expense = new Expense();
        expense.setOwner(owner);
        copyFields(expense, req);

        Expense saved = expenseRepository.save(expense);
        auditService.record(ownerId, "CREATE", "EXPENSE", String.valueOf(saved.getId()), "Expense created");
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<Expense> list(UUID ownerId, ExpenseCategory category, LocalDate from, LocalDate to,
                              Pageable pageable) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new ConflictException("Date range end cannot be before start");
        }
        boolean hasRange = from != null && to != null;
        if (category != null && hasRange) {
            return expenseRepository.findByOwnerIdAndCategoryAndExpenseDateBetween(
                    ownerId, category, from, to, pageable);
        }
        if (category != null) {
            return expenseRepository.findByOwnerIdAndCategory(ownerId, category, pageable);
        }
        if (hasRange) {
            return expenseRepository.findByOwnerIdAndExpenseDateBetween(ownerId, from, to, pageable);
        }
        return expenseRepository.findByOwnerId(ownerId, pageable);
    }

    @Transactional(readOnly = true)
    public Expense get(UUID ownerId, UUID id) {
        return loadOwned(ownerId, id);
    }

    @Transactional
    public Expense update(UUID ownerId, UUID id, ExpenseRequest req) {
        Expense expense = loadOwned(ownerId, id);
        copyFields(expense, req);
        Expense saved = expenseRepository.save(expense);
        auditService.record(ownerId, "UPDATE", "EXPENSE", String.valueOf(saved.getId()), "Expense updated");
        return saved;
    }

    @Transactional
    public void delete(UUID ownerId, UUID id) {
        Expense expense = loadOwned(ownerId, id);
        expenseRepository.delete(expense);
        auditService.record(ownerId, "DELETE", "EXPENSE", String.valueOf(id), "Expense deleted");
    }

    private Expense loadOwned(UUID ownerId, UUID id) {
        return expenseRepository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new NotFoundException("Expense not found"));
    }

    private void copyFields(Expense expense, ExpenseRequest req) {
        expense.setDescription(req.description());
        expense.setCategory(req.category());
        expense.setAmount(req.amount().setScale(2, RoundingMode.HALF_UP));
        expense.setExpenseDate(req.expenseDate());
        expense.setPaymentMethod(req.paymentMethod());
        expense.setVendor(req.vendor());
        expense.setNotes(req.notes());
    }
}
