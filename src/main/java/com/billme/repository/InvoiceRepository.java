package com.billme.repository;

import com.billme.invoice.Invoice;
import com.billme.invoice.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    // Customer invoice fetch
    List<Invoice> findByCustomer_User_Id(Long userId);

    List<Invoice> findByCustomer_User_IdAndStatus(Long userId, InvoiceStatus status);

    Optional<Invoice> findByIdAndCustomer_User_Id(Long invoiceId, Long userId);
    Optional<Invoice> findByRazorpayOrderId(String razorpayOrderId);
    // Merchant invoice fetch
    List<Invoice> findByMerchant_User_Id(Long userId);

    Optional<Invoice> findByIdAndMerchant_User_Id(Long invoiceId, Long userId);

    List<Invoice> findByMerchant_User_IdAndStatus(
            Long userId,
            InvoiceStatus status
    );
}