package com.billme.invoice;

import com.billme.customer.CustomerProfile;
import com.billme.invoice.dto.CreateInvoiceItemRequest;
import com.billme.invoice.CreateInvoiceRequest;
import com.billme.merchant.MerchantProfile;
import com.billme.product.Product;
import com.billme.repository.*;
import com.billme.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import com.billme.invoice.CustomerInvoiceResponse;
import java.math.BigDecimal;
import java.util.List;
import com.billme.invoice.CustomerInvoiceResponse;
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final MerchantProfileRepository merchantProfileRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    // =====================================================
    // CREATE ITEMIZED INVOICE
    // =====================================================
    @Transactional
    public void createInvoice(CreateInvoiceRequest request, String email) {

        User loggedInUser = getLoggedInUser();

        MerchantProfile merchant = merchantProfileRepository
                .findByUser_Id(loggedInUser.getId())
                .orElseThrow(() -> new RuntimeException("Merchant profile not found"));

        if (!merchant.isProfileCompleted()) {
            throw new RuntimeException("Complete profile before creating invoice");
        }

        CustomerProfile customer = customerProfileRepository
                .findById(request.getCustomerId())
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new RuntimeException("Invoice must contain items");
        }

        Invoice invoice = new Invoice();
        invoice.setMerchant(merchant);
        invoice.setCustomer(customer);

        BigDecimal grandTotal = BigDecimal.ZERO;

        for (CreateInvoiceItemRequest itemRequest : request.getItems()) {

            if (itemRequest.getQuantity() <= 0) {
                throw new RuntimeException("Quantity must be greater than zero");
            }

            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found"));

            if (!product.getMerchant().getId().equals(merchant.getId())) {
                throw new RuntimeException("Unauthorized product access");
            }

            BigDecimal total = product.getPrice()
                    .multiply(BigDecimal.valueOf(itemRequest.getQuantity()));

            InvoiceItem item = InvoiceItem.builder()
                    .invoice(invoice)
                    .product(product)
                    .productNameSnapshot(product.getName())
                    .unitPrice(product.getPrice())
                    .quantity(itemRequest.getQuantity())
                    .totalPrice(total)
                    .build();

            invoice.getItems().add(item);
            grandTotal = grandTotal.add(total);
        }

        invoice.setAmount(grandTotal);

        invoiceRepository.save(invoice);
    }

    // =====================================================
    private User getLoggedInUser() {

        String email = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
    public List<CustomerInvoiceResponse> getCustomerInvoices(String email) {
        throw new UnsupportedOperationException("Invoice Phase not finalized yet");
    }

    public List<CustomerInvoiceResponse> getPendingInvoices(String email) {
        throw new UnsupportedOperationException("Invoice Phase not finalized yet");
    }
    public CustomerInvoiceResponse getInvoiceById(Long id, String email) {
        throw new UnsupportedOperationException("Invoice Phase not finalized yet");
    }
}