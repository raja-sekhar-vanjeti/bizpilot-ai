package com.bizpilot.customer.service;

import com.bizpilot.audit.service.AuditService;
import com.bizpilot.auth.entity.User;
import com.bizpilot.auth.repository.UserRepository;
import com.bizpilot.common.exception.NotFoundException;
import com.bizpilot.customer.dto.CustomerRequest;
import com.bizpilot.customer.dto.CustomerResponse;
import com.bizpilot.customer.entity.Customer;
import com.bizpilot.customer.repository.CustomerRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public CustomerService(CustomerRepository customerRepository, UserRepository userRepository,
                           AuditService auditService) {
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public CustomerResponse create(UUID ownerId, CustomerRequest req) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        Customer customer = new Customer();
        customer.setOwner(owner);
        copyFields(customer, req);
        customer.setArchived(false);

        Customer saved = customerRepository.save(customer);
        auditService.record(ownerId, "CREATE", "CUSTOMER", String.valueOf(saved.getId()), "Customer created");
        return CustomerResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> list(UUID ownerId, String search, Pageable pageable) {
        Page<Customer> page = (search == null || search.isBlank())
                ? customerRepository.findByOwnerIdAndArchivedFalse(ownerId, pageable)
                : customerRepository.search(ownerId, search.trim(), pageable);
        return page.map(CustomerResponse::from);
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(UUID ownerId, UUID id) {
        return CustomerResponse.from(loadActive(ownerId, id));
    }

    @Transactional
    public CustomerResponse update(UUID ownerId, UUID id, CustomerRequest req) {
        Customer customer = loadActive(ownerId, id);
        copyFields(customer, req);
        Customer saved = customerRepository.save(customer);
        auditService.record(ownerId, "UPDATE", "CUSTOMER", String.valueOf(saved.getId()), "Customer updated");
        return CustomerResponse.from(saved);
    }

    /** Soft delete: marks the customer archived so invoice history is preserved. */
    @Transactional
    public void delete(UUID ownerId, UUID id) {
        Customer customer = loadActive(ownerId, id);
        customer.setArchived(true);
        customerRepository.save(customer);
        auditService.record(ownerId, "DELETE", "CUSTOMER", String.valueOf(id), "Customer archived");
    }

    private Customer loadActive(UUID ownerId, UUID id) {
        return customerRepository.findByIdAndOwnerId(id, ownerId)
                .filter(c -> !c.isArchived())
                .orElseThrow(() -> new NotFoundException("Customer not found"));
    }

    private void copyFields(Customer customer, CustomerRequest req) {
        customer.setName(req.name());
        customer.setEmail(req.email());
        customer.setPhone(req.phone());
        customer.setCompanyName(req.companyName());
        customer.setBillingAddress(req.billingAddress());
        customer.setNotes(req.notes());
    }
}
