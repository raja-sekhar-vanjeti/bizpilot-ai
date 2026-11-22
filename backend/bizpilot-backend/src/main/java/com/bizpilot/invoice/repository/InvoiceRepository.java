package com.bizpilot.invoice.repository;

import com.bizpilot.invoice.entity.Invoice;
import com.bizpilot.invoice.entity.InvoiceStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByIdAndOwnerId(UUID id, UUID ownerId);

    boolean existsByOwnerIdAndInvoiceNumber(UUID ownerId, String invoiceNumber);

    long countByOwnerId(UUID ownerId);

    long countByOwnerIdAndCustomerId(UUID ownerId, UUID customerId);

    List<Invoice> findByOwnerIdAndStatusNotIn(UUID ownerId, Collection<InvoiceStatus> statuses);

    Page<Invoice> findByOwnerId(UUID ownerId, Pageable pageable);

    Page<Invoice> findByOwnerIdAndStatus(UUID ownerId, InvoiceStatus status, Pageable pageable);

    Page<Invoice> findByOwnerIdAndCustomerId(UUID ownerId, UUID customerId, Pageable pageable);

    Page<Invoice> findByOwnerIdAndInvoiceDateBetween(UUID ownerId, LocalDate from, LocalDate to, Pageable pageable);
}
