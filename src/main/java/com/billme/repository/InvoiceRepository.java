package com.billme.repository;

import com.billme.invoice.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findByCustomerId(Long customerId);

    List<Invoice> findByMerchantId(Long merchantId);
}
