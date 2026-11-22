package com.bizpilot.demo;

import com.bizpilot.auth.dto.RegisterRequest;
import com.bizpilot.auth.entity.User;
import com.bizpilot.auth.repository.UserRepository;
import com.bizpilot.auth.service.AuthService;
import com.bizpilot.customer.dto.CustomerRequest;
import com.bizpilot.customer.dto.CustomerResponse;
import com.bizpilot.customer.service.CustomerService;
import com.bizpilot.expense.dto.ExpenseRequest;
import com.bizpilot.expense.entity.ExpenseCategory;
import com.bizpilot.expense.service.ExpenseService;
import com.bizpilot.invoice.dto.InvoiceItemRequest;
import com.bizpilot.invoice.dto.InvoiceRequest;
import com.bizpilot.invoice.dto.InvoiceResponse;
import com.bizpilot.invoice.service.InvoiceService;
import com.bizpilot.payment.entity.PaymentMethod;
import com.bizpilot.payment.service.PaymentService;
import com.bizpilot.reminder.service.ReminderService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Seeds a demo account with interrelated customers, invoices, payments, expenses and reminders.
 * Runs only when bizpilot.demo-seed.enabled=true. All data is created through the existing services.
 * Idempotent: if the demo user already owns customers, the seed is skipped.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "bizpilot.demo-seed.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final String CURRENCY = "INR";

    private final AuthService authService;
    private final UserRepository userRepository;
    private final CustomerService customerService;
    private final InvoiceService invoiceService;
    private final PaymentService paymentService;
    private final ExpenseService expenseService;
    private final ReminderService reminderService;
    private final String demoEmail;
    private final String demoPassword;

    public DemoDataSeeder(AuthService authService,
                          UserRepository userRepository,
                          CustomerService customerService,
                          InvoiceService invoiceService,
                          PaymentService paymentService,
                          ExpenseService expenseService,
                          ReminderService reminderService,
                          @Value("${bizpilot.demo-seed.email:demo@bizpilot.local}") String demoEmail,
                          @Value("${bizpilot.demo-seed.password:}") String demoPassword) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.customerService = customerService;
        this.invoiceService = invoiceService;
        this.paymentService = paymentService;
        this.expenseService = expenseService;
        this.reminderService = reminderService;
        this.demoEmail = demoEmail;
        this.demoPassword = demoPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (demoPassword == null || demoPassword.isBlank()) {
            log.warn("Demo data seed is enabled but bizpilot.demo-seed.password is blank; skipping seed.");
            return;
        }
        try {
            seed();
        } catch (Exception ex) {
            log.error("Demo data seed failed; application startup continues", ex);
        }
    }

    private void seed() {
        String email = demoEmail.trim().toLowerCase(Locale.ROOT);
        UUID ownerId = ensureDemoUser(email);

        if (customerService.list(ownerId, null, PageRequest.of(0, 1)).getTotalElements() > 0) {
            log.info("Demo data already present for {}; skipping seed.", email);
            return;
        }
        log.info("Seeding demo data for {}", email);

        // Customers
        UUID anita = customerService.create(ownerId, new CustomerRequest(
                "Anita Rao", "anita.rao@example.com", "+91-98100-00001",
                "Rao Design Studio", "12 MG Road, Bengaluru 560001", "Prefers invoices by email")).id();
        UUID vikram = customerService.create(ownerId, new CustomerRequest(
                "Vikram Mehta", "vikram.mehta@example.com", "+91-98100-00002",
                "Mehta Logistics LLP", "45 Ring Road, Ahmedabad 380015", null)).id();
        UUID priya = customerService.create(ownerId, new CustomerRequest(
                "Priya Nair", "priya.nair@example.com", "+91-98100-00003",
                null, "7 Marine Drive, Kochi 682011", null)).id();
        UUID arjun = customerService.create(ownerId, new CustomerRequest(
                "Arjun Patel", "arjun.patel@example.com", "+91-98100-00004",
                "Patel Foods", "88 Station Road, Pune 411001", "Net 15 terms")).id();
        UUID sara = customerService.create(ownerId, new CustomerRequest(
                "Sara Thomas", "sara.thomas@example.com", "+91-98100-00005",
                "Thomas & Co Accounting", "3 Park Street, Kolkata 700016", null)).id();
        UUID kabir = customerService.create(ownerId, new CustomerRequest(
                "Kabir Khan", "kabir.khan@example.com", "+91-98100-00006",
                null, "21 Brigade Road, Bengaluru 560025", null)).id();

        LocalDate today = LocalDate.now();

        // 1. PAID - full payment recorded
        InvoiceResponse paid = createInvoice(ownerId, anita, today.minusDays(40), today.minusDays(25),
                "0.00", "Thank you for your business",
                item("Website redesign", "1.00", "45000.00"),
                item("Hosting setup", "2.00", "1500.00"));
        invoiceService.markSent(ownerId, paid.id());
        paymentService.record(ownerId, paid.id(), paid.totalAmount(), today.minusDays(30),
                PaymentMethod.BANK_TRANSFER, "NEFT-DEMO-1001", "Full settlement");

        // 2. PAID - full payment with tax, paid via UPI
        InvoiceResponse paidTaxed = createInvoice(ownerId, vikram, today.minusDays(35), today.minusDays(20),
                "18.00", null,
                item("Fleet consulting - monthly", "3.00", "8000.00"));
        invoiceService.markSent(ownerId, paidTaxed.id());
        paymentService.record(ownerId, paidTaxed.id(), paidTaxed.totalAmount(), today.minusDays(22),
                PaymentMethod.UPI, "UPI-DEMO-2002", "Paid in full");

        // 3. PARTIALLY PAID - due date still in the future
        InvoiceResponse partialUpcoming = createInvoice(ownerId, priya, today.minusDays(20), today.plusDays(10),
                "0.00", null,
                item("Brand identity package", "1.00", "30000.00"));
        invoiceService.markSent(ownerId, partialUpcoming.id());
        paymentService.record(ownerId, partialUpcoming.id(), new BigDecimal("10000.00"), today.minusDays(15),
                PaymentMethod.CARD, "CARD-DEMO-3003", "First instalment");

        // 4. PARTIALLY PAID - due date already passed, so the service marks it OVERDUE
        InvoiceResponse partialOverdue = createInvoice(ownerId, arjun, today.minusDays(50), today.minusDays(35),
                "0.00", "Milestone billing",
                item("Catering contract - March", "2.00", "12500.00"));
        invoiceService.markSent(ownerId, partialOverdue.id());
        paymentService.record(ownerId, partialOverdue.id(), new BigDecimal("8000.00"), today.minusDays(33),
                PaymentMethod.CASH, null, "Part payment in cash");

        // 5. SENT and OVERDUE - no payment
        InvoiceResponse overdueTaxed = createInvoice(ownerId, sara, today.minusDays(45), today.minusDays(15),
                "18.00", null,
                item("Quarterly bookkeeping", "1.00", "18000.00"));
        invoiceService.markSent(ownerId, overdueTaxed.id());

        // 6. SENT and OVERDUE - no payment
        InvoiceResponse overdueFreelance = createInvoice(ownerId, kabir, today.minusDays(30), today.minusDays(5),
                "0.00", null,
                item("UI audit - freelance", "4.00", "3500.00"));
        invoiceService.markSent(ownerId, overdueFreelance.id());

        // 7. SENT and UPCOMING - due in about four weeks
        InvoiceResponse upcomingRetainer = createInvoice(ownerId, anita, today.minusDays(3), today.plusDays(27),
                "18.00", "Monthly retainer",
                item("Website maintenance retainer", "1.00", "9000.00"));
        invoiceService.markSent(ownerId, upcomingRetainer.id());

        // 8. SENT and UPCOMING - due within the next 7 days
        InvoiceResponse upcomingSoon = createInvoice(ownerId, vikram, today.minusDays(1), today.plusDays(5),
                "0.00", null,
                item("Route optimisation workshop", "1.00", "15000.00"));
        invoiceService.markSent(ownerId, upcomingSoon.id());

        // 9. SENT and UPCOMING
        InvoiceResponse upcomingLogo = createInvoice(ownerId, priya, today.minusDays(2), today.plusDays(14),
                "0.00", null,
                item("Logo animation", "1.00", "6000.00"));
        invoiceService.markSent(ownerId, upcomingLogo.id());

        // 10. DRAFT
        createInvoice(ownerId, sara, today, today.plusDays(30),
                "18.00", "Scope to be confirmed",
                item("Year-end tax filing support", "1.00", "12000.00"));

        // 11. DRAFT
        createInvoice(ownerId, kabir, today, today.plusDays(15),
                "0.00", null,
                item("Podcast cover art", "2.00", "2500.00"));

        // 12. CANCELLED - created then cancelled while still unpaid
        InvoiceResponse cancelled = createInvoice(ownerId, arjun, today.minusDays(4), today.plusDays(10),
                "0.00", "Client withdrew the order",
                item("Menu design", "1.00", "7000.00"));
        invoiceService.cancel(ownerId, cancelled.id());

        // Expenses across every category, all within the last 60 days
        createExpense(ownerId, "Office rent - current month", ExpenseCategory.RENT, "35000.00",
                today.minusDays(55), PaymentMethod.BANK_TRANSFER, "Skyline Properties");
        createExpense(ownerId, "Staff salaries - payroll run", ExpenseCategory.SALARY, "120000.00",
                today.minusDays(50), PaymentMethod.BANK_TRANSFER, "Internal payroll");
        createExpense(ownerId, "Accounting SaaS subscription", ExpenseCategory.SOFTWARE, "4500.00",
                today.minusDays(40), PaymentMethod.CARD, "CloudLedger Inc");
        createExpense(ownerId, "Search advertising campaign", ExpenseCategory.MARKETING, "12000.00",
                today.minusDays(30), PaymentMethod.CARD, "Search Ads Ltd");
        createExpense(ownerId, "Client visit - Chennai", ExpenseCategory.TRAVEL, "6800.00",
                today.minusDays(21), PaymentMethod.UPI, "Local travel agency");
        createExpense(ownerId, "Electricity bill", ExpenseCategory.UTILITIES, "3200.00",
                today.minusDays(15), PaymentMethod.UPI, "State Electricity Board");
        createExpense(ownerId, "Laptop for design team", ExpenseCategory.EQUIPMENT, "85000.00",
                today.minusDays(10), PaymentMethod.CARD, "TechHub Electronics");
        createExpense(ownerId, "Bank charges and misc fees", ExpenseCategory.OTHER, "850.00",
                today.minusDays(4), PaymentMethod.CASH, null);

        // Reminders for overdue and upcoming open balances, via the existing generator
        int reminders = reminderService.generate(ownerId).size();
        log.info("Demo data seeded for {}: 6 customers, 12 invoices, 4 payments, 8 expenses, {} reminders.",
                email, reminders);
    }

    private UUID ensureDemoUser(String email) {
        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            return existing.get().getId();
        }
        authService.register(new RegisterRequest(
                "Demo User", "Demo Business Pvt Ltd", email, demoPassword, "+91-98765-43210"));
        log.info("Created demo user {}", email);
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Demo user not found after registration"))
                .getId();
    }

    private InvoiceResponse createInvoice(UUID ownerId, UUID customerId, LocalDate invoiceDate,
                                          LocalDate dueDate, String taxPercent, String notes,
                                          InvoiceItemRequest... items) {
        InvoiceRequest request = new InvoiceRequest(
                customerId,
                invoiceDate,
                dueDate,
                new BigDecimal(taxPercent),
                new BigDecimal("0.00"),
                CURRENCY,
                notes,
                List.of(items));
        return invoiceService.create(ownerId, request);
    }

    private static InvoiceItemRequest item(String description, String quantity, String unitPrice) {
        return new InvoiceItemRequest(description, new BigDecimal(quantity), new BigDecimal(unitPrice));
    }

    private void createExpense(UUID ownerId, String description, ExpenseCategory category, String amount,
                               LocalDate expenseDate, PaymentMethod method, String vendor) {
        expenseService.create(ownerId, new ExpenseRequest(
                description, category, new BigDecimal(amount), expenseDate, method, vendor, null));
    }
}
