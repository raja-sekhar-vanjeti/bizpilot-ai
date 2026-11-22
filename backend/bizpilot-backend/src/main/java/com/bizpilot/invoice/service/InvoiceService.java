package com.bizpilot.invoice.service;

import com.bizpilot.auth.entity.User;
import com.bizpilot.auth.repository.UserRepository;
import com.bizpilot.common.exception.ConflictException;
import com.bizpilot.common.exception.NotFoundException;
import com.bizpilot.customer.entity.Customer;
import com.bizpilot.customer.repository.CustomerRepository;
import com.bizpilot.invoice.dto.InvoiceItemRequest;
import com.bizpilot.invoice.dto.InvoiceRequest;
import com.bizpilot.invoice.dto.InvoiceResponse;
import com.bizpilot.invoice.entity.Invoice;
import com.bizpilot.invoice.entity.InvoiceItem;
import com.bizpilot.invoice.entity.InvoiceStatus;
import com.bizpilot.invoice.repository.InvoiceRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceService {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;
    private static final int MAX_NUMBER_ATTEMPTS = 5;
    private static final String DEFAULT_CURRENCY = "INR";

    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          CustomerRepository customerRepository,
                          UserRepository userRepository) {
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public InvoiceResponse create(UUID ownerId, InvoiceRequest req) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        Customer customer = resolveCustomer(req, ownerId);

        if (req.dueDate().isBefore(req.invoiceDate())) {
            throw new ConflictException("Due date cannot be before invoice date");
        }

        Invoice invoice = new Invoice();
        invoice.setOwner(owner);
        invoice.setCustomer(customer);
        invoice.setInvoiceNumber(generateInvoiceNumber(ownerId));
        invoice.setInvoiceDate(req.invoiceDate());
        invoice.setDueDate(req.dueDate());
        invoice.setCurrency(normalizeCurrency(req.currency()));
        invoice.setNotes(req.notes());
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setTaxPercent(req.taxPercent() == null ? BigDecimal.ZERO : req.taxPercent());

        addItems(invoice, req);
        calculateTotals(invoice, discountOf(req));

        Invoice saved = invoiceRepository.save(invoice);
        return InvoiceResponse.from(saved);
    }

    String generateInvoiceNumber(UUID ownerId) {
        for (int attempt = 0; attempt < MAX_NUMBER_ATTEMPTS; attempt++) {
            String number = "INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            if (!invoiceRepository.existsByOwnerIdAndInvoiceNumber(ownerId, number)) {
                return number;
            }
        }
        throw new ConflictException("Could not allocate invoice number");
    }

    public void calculateTotals(Invoice inv, BigDecimal discount) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (InvoiceItem item : inv.getItems()) {
            subtotal = subtotal.add(item.getLineTotal());
        }
        subtotal = subtotal.setScale(MONEY_SCALE, MONEY_ROUNDING);

        BigDecimal safeDiscount = (discount == null ? BigDecimal.ZERO : discount)
                .setScale(MONEY_SCALE, MONEY_ROUNDING);
        if (safeDiscount.compareTo(subtotal) > 0) {
            throw new ConflictException("Discount cannot exceed subtotal");
        }

        BigDecimal base = subtotal.subtract(safeDiscount);
        BigDecimal taxPercent = inv.getTaxPercent() == null ? BigDecimal.ZERO : inv.getTaxPercent();
        BigDecimal taxAmount = base.multiply(taxPercent)
                .divide(BigDecimal.valueOf(100), MONEY_SCALE, MONEY_ROUNDING);
        BigDecimal total = base.add(taxAmount).setScale(MONEY_SCALE, MONEY_ROUNDING);

        if (inv.getPaidAmount() == null) {
            inv.setPaidAmount(BigDecimal.ZERO.setScale(MONEY_SCALE, MONEY_ROUNDING));
        }

        inv.setDiscountAmount(safeDiscount);
        inv.setSubtotal(subtotal);
        inv.setTaxAmount(taxAmount);
        inv.setTotalAmount(total);
        inv.setOutstandingAmount(total.subtract(inv.getPaidAmount()).setScale(MONEY_SCALE, MONEY_ROUNDING));
    }

    @Transactional
    public InvoiceResponse update(UUID ownerId, UUID id, InvoiceRequest req) {
        Invoice invoice = loadInvoice(ownerId, id);
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new ConflictException("Only draft invoices can be edited");
        }
        Customer customer = resolveCustomer(req, ownerId);

        if (req.dueDate().isBefore(req.invoiceDate())) {
            throw new ConflictException("Due date cannot be before invoice date");
        }

        invoice.setCustomer(customer);
        invoice.setInvoiceDate(req.invoiceDate());
        invoice.setDueDate(req.dueDate());
        invoice.setCurrency(normalizeCurrency(req.currency()));
        invoice.setNotes(req.notes());
        invoice.setTaxPercent(req.taxPercent() == null ? BigDecimal.ZERO : req.taxPercent());

        invoice.getItems().clear();
        addItems(invoice, req);
        calculateTotals(invoice, discountOf(req));

        Invoice saved = invoiceRepository.save(invoice);
        return InvoiceResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(UUID ownerId, UUID id) {
        return InvoiceResponse.from(loadInvoice(ownerId, id));
    }

    @Transactional(readOnly = true)
    public Page<InvoiceResponse> list(UUID ownerId, InvoiceStatus status, UUID customerId,
                                      LocalDate from, LocalDate to, Pageable pageable) {
        Page<Invoice> page;
        if (status != null) {
            page = invoiceRepository.findByOwnerIdAndStatus(ownerId, status, pageable);
        } else if (customerId != null) {
            page = invoiceRepository.findByOwnerIdAndCustomerId(ownerId, customerId, pageable);
        } else if (from != null && to != null) {
            page = invoiceRepository.findByOwnerIdAndInvoiceDateBetween(ownerId, from, to, pageable);
        } else {
            page = invoiceRepository.findByOwnerId(ownerId, pageable);
        }
        return page.map(InvoiceResponse::from);
    }

    @Transactional
    public InvoiceResponse markSent(UUID ownerId, UUID id) {
        Invoice invoice = loadInvoice(ownerId, id);
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new ConflictException("Only draft invoices can be sent");
        }
        invoice.setStatus(InvoiceStatus.SENT);
        invoice.setSentAt(LocalDateTime.now());
        Invoice saved = invoiceRepository.save(invoice);
        return InvoiceResponse.from(saved);
    }

    @Transactional
    public InvoiceResponse cancel(UUID ownerId, UUID id) {
        Invoice invoice = loadInvoice(ownerId, id);
        InvoiceStatus current = invoice.getStatus();
        if (current == InvoiceStatus.PAID || current == InvoiceStatus.CANCELLED) {
            throw new ConflictException("Invoice cannot be cancelled in its current status");
        }
        if (invoice.getPaidAmount() != null && invoice.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new ConflictException("Invoice with payments cannot be cancelled");
        }
        invoice.setStatus(InvoiceStatus.CANCELLED);
        Invoice saved = invoiceRepository.save(invoice);
        return InvoiceResponse.from(saved);
    }

    private Invoice loadInvoice(UUID ownerId, UUID id) {
        return invoiceRepository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new NotFoundException("Invoice not found"));
    }

    private Customer resolveCustomer(InvoiceRequest req, UUID ownerId) {
        return customerRepository.findByIdAndOwnerId(req.customerId(), ownerId)
                .orElseThrow(() -> new NotFoundException("Customer not found"));
    }

    private void addItems(Invoice invoice, InvoiceRequest req) {
        for (InvoiceItemRequest itemReq : req.items()) {
            InvoiceItem item = new InvoiceItem();
            item.setInvoice(invoice);
            item.setDescription(itemReq.description());
            BigDecimal quantity = itemReq.quantity().setScale(MONEY_SCALE, MONEY_ROUNDING);
            BigDecimal unitPrice = itemReq.unitPrice().setScale(MONEY_SCALE, MONEY_ROUNDING);
            item.setQuantity(quantity);
            item.setUnitPrice(unitPrice);
            item.setLineTotal(quantity.multiply(unitPrice).setScale(MONEY_SCALE, MONEY_ROUNDING));
            invoice.getItems().add(item);
        }
    }

    private static BigDecimal discountOf(InvoiceRequest req) {
        return req.discountAmount() == null ? BigDecimal.ZERO : req.discountAmount();
    }

    private static String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return DEFAULT_CURRENCY;
        }
        return currency.trim().toUpperCase();
    }
}
