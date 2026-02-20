package com.billme.repository;

import com.billme.merchant.MerchantProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MerchantProfileRepository extends JpaRepository<MerchantProfile, Long> {

    Optional<MerchantProfile> findByUser_Id(Long id);
    Optional<MerchantProfile> findByUser_Email(String email);}