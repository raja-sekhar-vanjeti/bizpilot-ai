package com.bizpilot.support;

import com.bizpilot.auth.entity.User;
import com.bizpilot.customer.entity.Customer;
import com.bizpilot.expense.entity.Expense;
import com.bizpilot.expense.entity.ExpenseCategory;
import com.bizpilot.invoice.entity.Invoice;
import com.bizpilot.invoice.entity.InvoiceStatus;
import com.bizpilot.payment.entity.Payment;
import com.bizpilot.payment.entity.PaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Small builders for entities used across unit tests. */
public final class TestData {

    private TestData() {
    }

    public static User user(UUID id) {
        User user = new User();
        user.setId(id);
        user.setFullName("Owner " + id.toString().substring(0, 4));
        user.setBusinessName("Business " + id.toString().substring(0, 4));
        user.setEmail(id + "@example.test");
        user.setPasswordHash("hash-not-for-output");
        return user;
    }

    public static Customer customer(User owner, String name) {
        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setOwner(owner);
        customer.setName(name);
        return customer;
    }

    public static Invoice invoice(User owner, Customer customer, String number, InvoiceStatus status,
                                  String total, String outstanding, LocalDate dueDate) {
        Invoice invoice = new Invoice();
        invoice.setId(UUID.randomUUID());
        invoice.setOwner(owner);
        invoice.setCustomer(customer);
        invoice.setInvoiceNumber(number);
        invoice.setStatus(status);
        invoice.setInvoiceDate(dueDate.minusDays(15));
        invoice.setDueDate(dueDate);
        invoice.setCurrency("INR");
        invoice.setTotalAmount(new BigDecimal(total));
        invoice.setOutstandingAmount(new BigDecimal(outstanding));
        invoice.setPaidAmount(new BigDecimal(total).subtract(new BigDecimal(outstanding)));
        return invoice;
    }

    public static Payment payment(User owner, Invoice invoice, String amount, LocalDate date, String reference) {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setOwner(owner);
        payment.setInvoice(invoice);
        payment.setAmount(new BigDecimal(amount));
        payment.setPaymentDate(date);
        payment.setMethod(PaymentMethod.UPI);
        payment.setReference(reference);
        return payment;
    }

    public static Expense expense(User owner, String description, String amount, LocalDate date) {
        Expense expense = new Expense();
        expense.setId(UUID.randomUUID());
        expense.setOwner(owner);
        expense.setDescription(description);
        expense.setCategory(ExpenseCategory.OTHER);
        expense.setPaymentMethod(PaymentMethod.CASH);
        expense.setAmount(new BigDecimal(amount));
        expense.setExpenseDate(date);
        return expense;
    }

    public static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
