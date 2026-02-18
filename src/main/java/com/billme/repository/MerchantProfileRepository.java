package com.billme.repository;

import com.billme.merchant.MerchantProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantProfileRepository
        extends JpaRepository<MerchantProfile, Long> {
}
