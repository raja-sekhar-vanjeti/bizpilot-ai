package com.bizpilot.customer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bizpilot.audit.service.AuditService;
import com.bizpilot.auth.entity.User;
import com.bizpilot.auth.repository.UserRepository;
import com.bizpilot.common.exception.NotFoundException;
import com.bizpilot.customer.dto.CustomerRequest;
import com.bizpilot.customer.dto.CustomerResponse;
import com.bizpilot.customer.entity.Customer;
import com.bizpilot.customer.repository.CustomerRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    private CustomerService customerService;

    private UUID ownerId;
    private UUID otherOwnerId;
    private UUID customerId;

    @BeforeEach
    void setUp() {
        customerService = new CustomerService(customerRepository, userRepository, auditService);
        ownerId = UUID.randomUUID();
        otherOwnerId = UUID.randomUUID();
        customerId = UUID.randomUUID();
    }

    private static CustomerRequest request(String name) {
        return new CustomerRequest(name, "jane@example.com", "555-0100", "Acme", "1 Main St", "VIP");
    }

    private User user(UUID id) {
        User user = new User();
        user.setId(id);
        user.setFullName("Owner Person");
        user.setBusinessName("Owner Biz");
        user.setEmail("owner@example.com");
        return user;
    }

    private Customer customer(User owner, boolean archived) {
        Customer customer = new Customer();
        customer.setId(customerId);
        customer.setOwner(owner);
        customer.setName("Jane Doe");
        customer.setArchived(archived);
        return customer;
    }

    @Test
    void createSavesCustomerOwnedByLoadedUserAndReturnsName() {
        User owner = user(ownerId);
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerResponse response = customerService.create(ownerId, request("Jane Doe"));

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        Customer saved = captor.getValue();
        assertEquals(owner, saved.getOwner());
        assertEquals("Jane Doe", saved.getName());
        assertEquals(false, saved.isArchived());
        assertEquals("Jane Doe", response.name());
    }

    @Test
    void createWithUnknownOwnerThrowsNotFound() {
        when(userRepository.findById(ownerId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> customerService.create(ownerId, request("Jane Doe")));

        verify(customerRepository, never()).save(any());
    }

    @Test
    void getCustomerOwnedByAnotherBusinessThrowsNotFoundAndUsesCallerOwnerId() {
        // Customer belongs to otherOwnerId; the caller is ownerId, so the scoped lookup finds nothing.
        when(customerRepository.findByIdAndOwnerId(customerId, ownerId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> customerService.get(ownerId, customerId));

        verify(customerRepository).findByIdAndOwnerId(customerId, ownerId);
        verify(customerRepository, never()).findByIdAndOwnerId(customerId, otherOwnerId);
    }

    @Test
    void getArchivedCustomerThrowsNotFound() {
        Customer archived = customer(user(ownerId), true);
        when(customerRepository.findByIdAndOwnerId(customerId, ownerId)).thenReturn(Optional.of(archived));

        assertThrows(NotFoundException.class, () -> customerService.get(ownerId, customerId));
    }

    @Test
    void deleteMarksCustomerArchivedAndDoesNotHardDelete() {
        Customer active = customer(user(ownerId), false);
        when(customerRepository.findByIdAndOwnerId(customerId, ownerId)).thenReturn(Optional.of(active));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        customerService.delete(ownerId, customerId);

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        assertEquals(true, captor.getValue().isArchived());
        verify(customerRepository, never()).delete(any());
    }

    @Test
    void updateUnownedCustomerThrowsNotFoundAndNeverSaves() {
        when(customerRepository.findByIdAndOwnerId(customerId, ownerId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> customerService.update(ownerId, customerId, request("New Name")));

        verify(customerRepository, never()).save(any());
    }
}
