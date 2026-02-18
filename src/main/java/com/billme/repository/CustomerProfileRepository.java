package com.billme.repository;

import com.billme.customer.CustomerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerProfileRepository
        extends JpaRepository<CustomerProfile, Long> {
}
