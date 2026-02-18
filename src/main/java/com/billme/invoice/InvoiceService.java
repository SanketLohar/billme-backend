package com.billme.invoice;

import com.billme.customer.CustomerProfile;
import com.billme.merchant.MerchantProfile;
import com.billme.repository.CustomerProfileRepository;
import com.billme.repository.InvoiceRepository;
import com.billme.repository.MerchantProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final MerchantProfileRepository merchantProfileRepository;
    private final CustomerProfileRepository customerProfileRepository;

    public void createInvoice(Long merchantId,
                              Long customerId,
                              BigDecimal amount) {

        MerchantProfile merchant =
                merchantProfileRepository.findById(merchantId)
                        .orElseThrow(() -> new RuntimeException("Merchant not found"));

        CustomerProfile customer =
                customerProfileRepository.findById(customerId)
                        .orElseThrow(() -> new RuntimeException("Customer not found"));

        Invoice invoice = new Invoice();
        invoice.setMerchant(merchant);
        invoice.setCustomer(customer);
        invoice.setAmount(amount);
        invoice.setStatus(InvoiceStatus.UNPAID);

        invoiceRepository.save(invoice);
    }
}
