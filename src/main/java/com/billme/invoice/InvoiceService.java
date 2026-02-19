package com.billme.invoice;

import com.billme.customer.CustomerProfile;
import com.billme.merchant.MerchantProfile;
import com.billme.repository.CustomerProfileRepository;
import com.billme.repository.InvoiceRepository;
import com.billme.repository.MerchantProfileRepository;
import com.billme.repository.UserRepository;
import com.billme.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final MerchantProfileRepository merchantProfileRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final UserRepository userRepository;

    // =====================================================
    // 1️⃣ CREATE INVOICE (MERCHANT)
    // =====================================================
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

        invoice.setInvoiceNumber(generateInvoiceNumber());
        invoice.setMerchant(merchant);
        invoice.setCustomer(customer);
        invoice.setAmount(amount);
        invoice.setStatus(InvoiceStatus.UNPAID);
        invoice.setIssuedAt(LocalDateTime.now());
        invoice.setPaymentMethod(null);
        invoice.setPaidAt(null);

        invoiceRepository.save(invoice);
    }

    // =====================================================
    // 2️⃣ GET ALL INVOICES (CUSTOMER)
    // =====================================================
    public List<CustomerInvoiceResponse> getCustomerInvoices() {

        User user = getLoggedInUser();

        return invoiceRepository
                .findByCustomer_User_Id(user.getId())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // =====================================================
    // 3️⃣ GET ONLY UNPAID INVOICES
    // =====================================================
    public List<CustomerInvoiceResponse> getPendingInvoices() {

        User user = getLoggedInUser();

        return invoiceRepository
                .findByCustomer_User_IdAndStatus(
                        user.getId(),
                        InvoiceStatus.UNPAID
                )
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // =====================================================
    // 4️⃣ GET SINGLE INVOICE (OWNERSHIP CHECK)
    // =====================================================
    public CustomerInvoiceResponse getInvoiceById(Long invoiceId) {

        User user = getLoggedInUser();

        Invoice invoice = invoiceRepository
                .findByIdAndCustomer_User_Id(invoiceId, user.getId())
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        return mapToResponse(invoice);
    }

    // =====================================================
    // 🔒 HELPER METHODS
    // =====================================================

    private String generateInvoiceNumber() {
        return "INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private User getLoggedInUser() {

        String email = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private CustomerInvoiceResponse mapToResponse(Invoice invoice) {

        return CustomerInvoiceResponse.builder()
                .id(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .merchantName(invoice.getMerchant().getBusinessName())
                .amount(invoice.getAmount())
                .status(invoice.getStatus())
                .issuedAt(invoice.getIssuedAt())
                .paidAt(invoice.getPaidAt())
                .build();
    }
}
