package com.billme.invoice;

import com.billme.customer.CustomerProfile;
import com.billme.invoice.dto.CreateInvoiceItemRequest;
import com.billme.invoice.dto.CustomerInvoiceResponse;
import com.billme.invoice.dto.InvoiceItemResponse;
import com.billme.merchant.MerchantProfile;
import com.billme.payment.RazorpayService;
import com.billme.product.Product;
import com.billme.repository.*;
import com.billme.transaction.Transaction;
import com.billme.transaction.TransactionStatus;
import com.billme.transaction.TransactionType;
import com.billme.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.billme.wallet.Wallet;
import com.billme.transaction.Transaction;
import com.billme.transaction.TransactionType;
import com.billme.transaction.TransactionStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final MerchantProfileRepository merchantProfileRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final RazorpayService razorpayService;

    @Value("${processing.fee.percent}")
    private BigDecimal processingFeePercent;
    // =====================================================
    // CREATE INVOICE (STRICT PHASE 6 VERSION)
    // =====================================================
    @Transactional
    public void createInvoice(CreateInvoiceRequest request) {

        User user = getLoggedInUser();

        MerchantProfile merchant = merchantProfileRepository
                .findByUser_Id(user.getId())
                .orElseThrow(() -> new RuntimeException("Merchant profile not found"));

        if (!merchant.isProfileCompleted()) {
            throw new RuntimeException("Complete profile before creating invoice");
        }

        CustomerProfile customer = customerProfileRepository
                .findById(request.getCustomerId())
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new RuntimeException("Invoice must contain at least one item");
        }

        Invoice invoice = new Invoice();
        invoice.setMerchant(merchant);
        invoice.setCustomer(customer);

        BigDecimal subtotal = BigDecimal.ZERO;

        for (CreateInvoiceItemRequest itemRequest : request.getItems()) {

            if (itemRequest.getQuantity() == null || itemRequest.getQuantity() <= 0) {
                throw new RuntimeException("Quantity must be greater than zero");
            }

            Product product = resolveProduct(itemRequest, merchant);

            if (!product.isActive()) {
                throw new RuntimeException("Product is not active");
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
            subtotal = subtotal.add(total);
        }

        // ================================
        // 💰 PROCESSING FEE CALCULATION
        // ================================

        BigDecimal processingFee = subtotal
                .multiply(processingFeePercent)
                .divide(BigDecimal.valueOf(100), 2, BigDecimal.ROUND_HALF_UP);

        BigDecimal totalPayable = subtotal.add(processingFee);

        invoice.setSubtotal(subtotal);
        invoice.setProcessingFee(processingFee);
        invoice.setTotalPayable(totalPayable);

        // amount = merchant earning base
        invoice.setAmount(subtotal);

        invoiceRepository.save(invoice);
    }

    // =====================================================
    // PRODUCT RESOLUTION (ID OR BARCODE)
    // =====================================================
    private Product resolveProduct(CreateInvoiceItemRequest itemRequest,
                                   MerchantProfile merchant) {

        if (itemRequest.getProductId() != null) {
            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found"));

            if (!product.getMerchant().getId().equals(merchant.getId())) {
                throw new RuntimeException("Unauthorized product access");
            }

            return product;
        }

        if (itemRequest.getBarcode() != null && !itemRequest.getBarcode().isBlank()) {
            return productRepository
                    .findByMerchantAndBarcode(merchant, itemRequest.getBarcode())
                    .orElseThrow(() -> new RuntimeException("Product not found for barcode"));
        }

        throw new RuntimeException("Product identifier (productId or barcode) required");
    }

    // =====================================================
    // CUSTOMER RETRIEVAL APIs
    // =====================================================
    @Transactional(readOnly = true)
    public List<CustomerInvoiceResponse> getCustomerInvoices(String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return invoiceRepository.findByCustomer_User_Id(user.getId())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CustomerInvoiceResponse> getPendingInvoices(String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return invoiceRepository
                .findByCustomer_User_IdAndStatus(user.getId(), InvoiceStatus.UNPAID)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CustomerInvoiceResponse getInvoiceById(Long id, String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Invoice invoice = invoiceRepository
                .findByIdAndCustomer_User_Id(id, user.getId())
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        return mapToResponse(invoice);
    }

    // =====================================================
    // STATE MACHINE ENFORCEMENT
    // =====================================================
    private void validateStatusTransition(Invoice invoice, InvoiceStatus newStatus) {

        InvoiceStatus current = invoice.getStatus();

        if (current == InvoiceStatus.PAID) {
            throw new RuntimeException("Paid invoice cannot be modified");
        }

        if (current == InvoiceStatus.UNPAID &&
                (newStatus == InvoiceStatus.PAID || newStatus == InvoiceStatus.FAILED || newStatus == InvoiceStatus.PENDING)) {
            return;
        }

        if (current == InvoiceStatus.PENDING &&
                (newStatus == InvoiceStatus.PAID || newStatus == InvoiceStatus.FAILED)) {
            return;
        }

        throw new RuntimeException("Invalid invoice status transition");
    }

    @Transactional
    public void markInvoicePaid(Long invoiceId) {

        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        validateStatusTransition(invoice, InvoiceStatus.PAID);

        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(LocalDateTime.now());
    }

    @Transactional
    public void markInvoiceFailed(Long invoiceId) {

        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        validateStatusTransition(invoice, InvoiceStatus.FAILED);

        invoice.setStatus(InvoiceStatus.FAILED);
    }

    // =====================================================
    // MAPPING
    // =====================================================
    private CustomerInvoiceResponse mapToResponse(Invoice invoice) {

        return CustomerInvoiceResponse.builder()
                .invoiceId(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .amount(invoice.getTotalPayable()) // 👈 updated
                .subtotal(invoice.getSubtotal())   // 👈 new
                .processingFee(invoice.getProcessingFee()) // 👈 new
                .totalPayable(invoice.getTotalPayable()) // 👈 new
                .status(invoice.getStatus().name())
                .paymentMethod(invoice.getPaymentMethod() != null
                        ? invoice.getPaymentMethod().name()
                        : null)
                .issuedAt(invoice.getIssuedAt())
                .paidAt(invoice.getPaidAt())
                .items(
                        invoice.getItems().stream()
                                .map(item ->
                                        InvoiceItemResponse.builder()
                                                .productName(item.getProductNameSnapshot())
                                                .unitPrice(item.getUnitPrice())
                                                .quantity(item.getQuantity())
                                                .totalPrice(item.getTotalPrice())
                                                .build()
                                ).toList()
                )
                .build();
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
    @Transactional
    public void payInvoiceWithFacePay(Long invoiceId) {

        User user = getLoggedInUser();

        // 🔐 Fetch invoice securely (customer ownership)
        Invoice invoice = invoiceRepository
                .findByIdAndCustomer_User_Id(invoiceId, user.getId())
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        // 🔒 Must be UNPAID
        if (invoice.getStatus() != InvoiceStatus.UNPAID) {
            throw new RuntimeException("Invoice is not payable");
        }

        CustomerProfile customer = invoice.getCustomer();
        MerchantProfile merchant = invoice.getMerchant();

        // Fetch wallets
        Wallet customerWallet = customer.getUser().getWallet();
        Wallet merchantWallet = merchant.getUser().getWallet();

        BigDecimal amount = invoice.getAmount();

        // 🔒 Check balance
        if (customerWallet.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient wallet balance");
        }

        // 💸 Debit customer
        customerWallet.setBalance(customerWallet.getBalance().subtract(amount));

        // 💰 Credit merchant
        merchantWallet.setBalance(merchantWallet.getBalance().add(amount));

        // 🧾 Create transaction
        Transaction transaction = Transaction.builder()
                .senderWallet(customerWallet)
                .receiverWallet(merchantWallet)
                .amount(amount)
                .transactionType(TransactionType.FACE_PAY)
                .status(TransactionStatus.SUCCESS)
                .createdAt(java.time.LocalDateTime.now())
                .build();

        invoice.setTransaction(transaction);
        invoice.setPaymentMethod(PaymentMethod.FACE_PAY);

        // 🔒 State machine enforcement
        validateStatusTransition(invoice, InvoiceStatus.PAID);

        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(java.time.LocalDateTime.now());
    }
    @Transactional
    public String createRazorpayOrder(Long invoiceId) {

        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        if (invoice.getStatus() != InvoiceStatus.UNPAID) {
            throw new RuntimeException("Only unpaid invoices can be paid");
        }

        var order = razorpayService.createOrder(invoice);

        invoice.setRazorpayOrderId(order.get("id"));
        invoice.setStatus(InvoiceStatus.PENDING);

        return order.get("id");
    }
}