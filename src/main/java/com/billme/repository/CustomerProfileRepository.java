package com.billme.repository;

import com.billme.customer.CustomerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerProfileRepository
        extends JpaRepository<CustomerProfile, Long> {
    Optional<CustomerProfile> findByUser_Id(Long userId);

}
