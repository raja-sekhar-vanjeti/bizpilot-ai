package com.bizpilot.payment.repository;

import com.bizpilot.payment.entity.Payment;
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

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByInvoiceIdAndOwnerId(UUID invoiceId, UUID ownerId);

    Optional<Payment> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<Payment> findByOwnerIdOrderByPaymentDateDesc(UUID ownerId);

    Page<Payment> findByOwnerId(UUID ownerId, Pageable pageable);

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.invoice.id = :invoiceId and p.owner.id = :ownerId")
    BigDecimal sumAmountByInvoiceIdAndOwnerId(@Param("invoiceId") UUID invoiceId, @Param("ownerId") UUID ownerId);

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.owner.id = :ownerId and p.paymentDate <= :date")
    BigDecimal sumAmountByOwnerIdUpTo(@Param("ownerId") UUID ownerId, @Param("date") LocalDate date);

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.owner.id = :ownerId and p.paymentDate between :from and :to")
    BigDecimal sumAmountByOwnerIdBetween(@Param("ownerId") UUID ownerId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    List<Payment> findByOwnerIdAndPaymentDateBetween(UUID ownerId, LocalDate from, LocalDate to);
}
