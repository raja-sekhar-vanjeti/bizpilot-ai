package com.bizpilot.customer.repository;

import com.bizpilot.customer.entity.Customer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Page<Customer> findByOwnerIdAndArchivedFalse(UUID ownerId, Pageable pageable);

    Optional<Customer> findByIdAndOwnerId(UUID id, UUID ownerId);

    @Query("select c from Customer c where c.owner.id = :ownerId and c.archived = false and (lower(c.name) like lower(concat('%', :term, '%')) or lower(c.email) like lower(concat('%', :term, '%')))")
    Page<Customer> search(@Param("ownerId") UUID ownerId, @Param("term") String term, Pageable pageable);
}
