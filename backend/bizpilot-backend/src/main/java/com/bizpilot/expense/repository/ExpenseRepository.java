package com.bizpilot.expense.repository;

import com.bizpilot.expense.entity.Expense;
import com.bizpilot.expense.entity.ExpenseCategory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    Optional<Expense> findByIdAndOwnerId(UUID id, UUID ownerId);

    Page<Expense> findByOwnerId(UUID ownerId, Pageable pageable);

    Page<Expense> findByOwnerIdAndCategory(UUID ownerId, ExpenseCategory category, Pageable pageable);

    Page<Expense> findByOwnerIdAndExpenseDateBetween(UUID ownerId, LocalDate from, LocalDate to, Pageable pageable);

    Page<Expense> findByOwnerIdAndCategoryAndExpenseDateBetween(
            UUID ownerId, ExpenseCategory category, LocalDate from, LocalDate to, Pageable pageable);

    @Query("select coalesce(sum(e.amount), 0) from Expense e where e.owner.id = :ownerId and e.expenseDate <= :date")
    BigDecimal sumAmountByOwnerIdUpTo(@Param("ownerId") UUID ownerId, @Param("date") LocalDate date);

    @Query("select coalesce(sum(e.amount), 0) from Expense e where e.owner.id = :ownerId and e.expenseDate between :from and :to")
    BigDecimal sumAmountByOwnerIdBetween(@Param("ownerId") UUID ownerId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    List<Expense> findByOwnerIdAndExpenseDateBetweenOrderByExpenseDateAsc(UUID ownerId, LocalDate from, LocalDate to);
}
