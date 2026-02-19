package com.billme.repository;

import com.billme.invoice.Invoice;
import com.billme.invoice.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    // 🔹 Fetch all invoices of a customer
    List<Invoice> findByCustomer_User_Id(Long userId);

    // 🔹 Fetch by customer + status (useful for filters)
    List<Invoice> findByCustomer_User_IdAndStatus(Long userId, InvoiceStatus status);

    // 🔐 Secure ownership validation (used in payment flow)
    Optional<Invoice> findByIdAndCustomer_User_Id(Long invoiceId, Long userId);

    // 🔐 Merchant-side invoice fetch (future use)
    List<Invoice> findByMerchant_User_Id(Long userId);

    // 🔐 Merchant invoice ownership validation (future use)
    Optional<Invoice> findByIdAndMerchant_User_Id(Long invoiceId, Long userId);
}
