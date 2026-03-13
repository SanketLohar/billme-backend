package com.billme.invoice;

import com.billme.customer.CustomerProfile;
import com.billme.email.InvoiceEmailService;
import com.billme.invoice.dto.CreateInvoiceItemRequest;
import com.billme.invoice.dto.CustomerInvoiceResponse;
import com.billme.invoice.dto.InvoiceItemResponse;
import com.billme.invoice.dto.PublicInvoiceResponse;
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
import com.billme.wallet.WalletService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final MerchantProfileRepository merchantProfileRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final RazorpayService razorpayService;
    private final InvoiceEmailService invoiceEmailService;
    private final WalletService walletService;
    private final InvoicePdfService invoicePdfService;
    @Value("${processing.fee.percent}")
    private BigDecimal processingFeePercent;

    private String generatePaymentToken() {
        return UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 10);
    }
    // =====================================================
    // CREATE INVOICE (STRICT PHASE 6 VERSION)
    // =====================================================
    @Transactional
    public void createInvoice(CreateInvoiceRequest request) {

        User user = getLoggedInUser();

        MerchantProfile merchant = merchantProfileRepository
                .findByUser_Id(user.getId())
                .orElseThrow(() -> new RuntimeException("Merchant profile not found"));

        if (merchant.getBusinessName() == null || merchant.getBusinessName().isBlank() ||
            merchant.getGstin() == null || merchant.getGstin().isBlank() ||
            merchant.getAddress() == null || merchant.getAddress().isBlank()) {
            throw new RuntimeException("Merchant profile incomplete. Please update profile.");
        }

        if (request.getCustomerEmail() == null || request.getCustomerEmail().isBlank()) {
            throw new RuntimeException("Customer email is required");
        }

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new RuntimeException("Invoice must contain at least one item");
        }

        Invoice invoice = new Invoice();
        invoice.setMerchant(merchant);
        invoice.setCustomerEmail(request.getCustomerEmail());
        invoice.setCustomerName(request.getCustomerName() != null ? request.getCustomerName() : "Customer");

        // Try linking customer profile if exists
        userRepository.findByEmail(request.getCustomerEmail()).ifPresent(u -> {
            customerProfileRepository.findByUser_Id(u.getId()).ifPresent(invoice::setCustomer);
        });

        // 🔐 Generate secure payment token BEFORE saving
        String paymentToken = generatePaymentToken();
        invoice.setPaymentToken(paymentToken);

        // 📍 Determine Place of Supply (customer location)
        String placeOfSupply = request.getCustomerState();
        if ((placeOfSupply == null || placeOfSupply.isBlank()) && invoice.getCustomer() != null) {
            placeOfSupply = invoice.getCustomer().getState();
        }
        if (placeOfSupply == null || placeOfSupply.isBlank()) {
            placeOfSupply = merchant.getState(); // Default to intra-state if unknown
        }

        boolean isIntraState = merchant.getState() != null && merchant.getState().equalsIgnoreCase(placeOfSupply);

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal cgstTotal = BigDecimal.ZERO;
        BigDecimal sgstTotal = BigDecimal.ZERO;
        BigDecimal igstTotal = BigDecimal.ZERO;

        for (CreateInvoiceItemRequest itemRequest : request.getItems()) {

            if (itemRequest.getQuantity() == null || itemRequest.getQuantity() <= 0) {
                throw new RuntimeException("Quantity must be greater than zero");
            }

            Product product = resolveProduct(itemRequest, merchant);

            if (!product.isActive()) {
                throw new RuntimeException("Product is not active");
            }

            BigDecimal quantity = BigDecimal.valueOf(itemRequest.getQuantity());
            BigDecimal unitPrice = product.getPrice();

            // Base amount (price × quantity)
            BigDecimal baseAmount = unitPrice.multiply(quantity);

            // GST rate
            BigDecimal gstRate = merchant.isGstRegistered()
                    ? BigDecimal.valueOf(product.getGstRate())
                    : BigDecimal.ZERO;

            // Per-item GST amount
            BigDecimal itemGst = baseAmount
                    .multiply(gstRate)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            BigDecimal itemCgst = BigDecimal.ZERO;
            BigDecimal itemSgst = BigDecimal.ZERO;
            BigDecimal itemIgst = BigDecimal.ZERO;

            if (isIntraState) {
                itemCgst = itemGst.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                itemSgst = itemGst.subtract(itemCgst); // Avoid rounding mismatch
            } else {
                itemIgst = itemGst;
            }

            // Total for this line item
            BigDecimal lineTotal = baseAmount.add(itemGst);

            InvoiceItem item = InvoiceItem.builder()
                    .invoice(invoice)
                    .product(product)
                    .productNameSnapshot(product.getName())
                    .unitPrice(unitPrice)
                    .quantity(itemRequest.getQuantity())
                    .baseAmount(baseAmount)
                    .gstRate(gstRate)
                    .gstAmount(itemGst)
                    .cgstAmount(itemCgst)
                    .sgstAmount(itemSgst)
                    .igstAmount(itemIgst)
                    .totalPrice(lineTotal)
                    .build();

            invoice.getItems().add(item);

            subtotal = subtotal.add(baseAmount);
            cgstTotal = cgstTotal.add(itemCgst);
            sgstTotal = sgstTotal.add(itemSgst);
            igstTotal = igstTotal.add(itemIgst);
        }

        BigDecimal totalGst = cgstTotal.add(sgstTotal).add(igstTotal);

        BigDecimal processingFee = subtotal
                .multiply(processingFeePercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal totalPayable = subtotal
                .add(totalGst)
                .add(processingFee)
                .setScale(2, RoundingMode.HALF_UP);

        // Store totals in invoice
        invoice.setSubtotal(subtotal);
        invoice.setCgstTotal(cgstTotal);
        invoice.setSgstTotal(sgstTotal);
        invoice.setIgstTotal(igstTotal);
        invoice.setGstTotal(totalGst);
        invoice.setProcessingFee(processingFee);
        invoice.setTotalPayable(totalPayable);

        // Customer payment amount
        invoice.setAmount(totalPayable);

        Invoice savedInvoice = invoiceRepository.save(invoice);

// Send invoice email
        invoiceEmailService.sendInvoiceEmail(savedInvoice);
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

        List<Invoice> invoices =
                invoiceRepository.findByCustomerUserEmail(email);

        return invoices.stream()
                .map(this::mapToResponse)
                .toList();
    }

    public byte[] generateInvoicePdf(Long invoiceId) {

        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        return invoicePdfService.generateInvoicePdf(invoice);
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
    // MERCHANT INVOICE RETRIEVAL
    // =====================================================
    @Transactional(readOnly = true)
    public List<CustomerInvoiceResponse> getMerchantInvoices(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return invoiceRepository.findByMerchant_User_Id(user.getId())
                .stream()
                .map(this::mapToResponse)
                .toList();
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

        BigDecimal cgst = invoice.getCgstTotal() != null ? invoice.getCgstTotal() : BigDecimal.ZERO;
        BigDecimal sgst = invoice.getSgstTotal() != null ? invoice.getSgstTotal() : BigDecimal.ZERO;
        BigDecimal igst = invoice.getIgstTotal() != null ? invoice.getIgstTotal() : BigDecimal.ZERO;

        // Backward compatibility for legacy invoices
        if (invoice.getCgstTotal() == null && invoice.getIgstTotal() == null && invoice.getGstTotal() != null) {
            cgst = invoice.getGstTotal().divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            sgst = invoice.getGstTotal().subtract(cgst);
        }

        return CustomerInvoiceResponse.builder()
                .invoiceId(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .amount(invoice.getTotalPayable())
                .subtotal(invoice.getSubtotal())
                .processingFee(invoice.getProcessingFee())
                .totalPayable(invoice.getTotalPayable())
                .cgstAmount(cgst)
                .sgstAmount(sgst)
                .igstAmount(igst)
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
                                                .gstRate(item.getGstRate())
                                                .gstAmount(item.getGstAmount())
                                                .cgstAmount(item.getCgstAmount())
                                                .sgstAmount(item.getSgstAmount())
                                                .igstAmount(item.getIgstAmount())
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

        BigDecimal customerPayment = invoice.getTotalPayable();
        BigDecimal processingFee = invoice.getProcessingFee();
        BigDecimal merchantSettlement = customerPayment.subtract(processingFee);

        if (!customerPayment.equals(merchantSettlement.add(processingFee))) {
            throw new IllegalStateException("Financial reconciliation failed");
        }

        if (customerWallet.getBalance().compareTo(customerPayment) < 0) {
            throw new RuntimeException("Insufficient wallet balance");
        }

        String referenceId = "FACEPAY-" + invoice.getInvoiceNumber();

        walletService.debit(customerWallet, customerPayment, referenceId);
        walletService.creditEscrow(merchantWallet, merchantSettlement, referenceId);

        Transaction transaction = Transaction.builder()
                .senderWallet(customerWallet)
                .receiverWallet(merchantWallet)
                .amount(customerPayment)
                .invoiceAmount(customerPayment)
                .processingFee(processingFee)
                .merchantSettlement(merchantSettlement)
                .transactionType(TransactionType.FACE_PAY)
                .status(TransactionStatus.SUCCESS)
                .createdAt(LocalDateTime.now())
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

        if (invoice.getStatus() != InvoiceStatus.UNPAID &&
                invoice.getStatus() != InvoiceStatus.PENDING) {
            throw new RuntimeException("Only unpaid invoices can be paid");
        }

        // 🔥 Payment lock check
        if (Boolean.TRUE.equals(invoice.getPaymentInProgress())) {

            // Check timeout
            if (invoice.getPaymentStartedAt() != null &&
                    invoice.getPaymentStartedAt().isBefore(LocalDateTime.now().minusMinutes(10))) {

                // 🔥 release stale payment lock
                invoice.setPaymentInProgress(false);
                invoice.setPaymentStartedAt(null);

                // 🔥 clear old Razorpay order
                invoice.setRazorpayOrderId(null);

                // 🔥 reset invoice state
                invoice.setStatus(InvoiceStatus.UNPAID);
            } else {
                throw new RuntimeException("Payment already in progress");
            }
        }

        invoice.setPaymentInProgress(true);
        invoice.setPaymentStartedAt(LocalDateTime.now());

        var order = razorpayService.createOrder(invoice);

        invoice.setRazorpayOrderId(order.get("id"));
        invoice.setStatus(InvoiceStatus.PENDING);

        return order.get("id");
    }

    @Transactional(readOnly = true)
    public PublicInvoiceResponse getPublicInvoice(String invoiceNumber, String token) {

        Invoice invoice = invoiceRepository.findByInvoiceNumber(invoiceNumber)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        if (!invoice.getPaymentToken().equals(token)) {
            throw new RuntimeException("Invalid payment token");
        }

        MerchantProfile merchant = invoice.getMerchant();

        BigDecimal gstTotal = invoice.getItems().stream()
                .map(InvoiceItem::getGstAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<InvoiceItemResponse> items = invoice.getItems().stream()
                .map(item -> InvoiceItemResponse.builder()
                        .productName(item.getProductNameSnapshot())
                        .unitPrice(item.getUnitPrice())
                        .quantity(item.getQuantity())
                        .gstRate(item.getGstRate())
                        .gstAmount(item.getGstAmount())
                        .totalPrice(item.getTotalPrice())
                        .build())
                .toList();

        return new PublicInvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                merchant.getBusinessName(),
                merchant.getGstin(),
                invoice.getResolvedCustomerEmail(),
                items,
                invoice.getSubtotal(),
                invoice.getCgstTotal() != null ? invoice.getCgstTotal() : BigDecimal.ZERO,
                invoice.getSgstTotal() != null ? invoice.getSgstTotal() : BigDecimal.ZERO,
                invoice.getIgstTotal() != null ? invoice.getIgstTotal() : BigDecimal.ZERO,
                invoice.getGstTotal() != null ? invoice.getGstTotal() : BigDecimal.ZERO,
                invoice.getProcessingFee(),
                invoice.getTotalPayable(),
                invoice.getStatus().name()
        );


    }
}