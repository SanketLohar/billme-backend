package com.billme.repository;

import com.billme.payment.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentTransactionRepository
        extends JpaRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction>
    findByRazorpayPaymentId(String razorpayPaymentId);
}