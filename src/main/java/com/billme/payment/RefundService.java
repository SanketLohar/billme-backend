package com.billme.payment;

import com.billme.invoice.Invoice;
import com.billme.invoice.InvoiceStatus;
import com.billme.invoice.PaymentMethod;
import com.billme.payment.dto.MerchantRefundResponse;
import com.billme.repository.InvoiceRepository;
import com.billme.repository.TransactionRepository;
import com.billme.transaction.Transaction;
import com.billme.transaction.TransactionStatus;
import com.billme.transaction.TransactionType;
import com.billme.user.Role;
import com.billme.user.User;
import com.billme.wallet.Wallet;
import com.billme.wallet.WalletService;
import com.billme.notification.NotificationService;
import com.billme.notification.NotificationType;
import com.billme.email.RefundEmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import com.billme.repository.UserRepository;
import com.billme.repository.WalletRepository;
@Service
@RequiredArgsConstructor
public class RefundService {

    private final InvoiceRepository invoiceRepository;
    private final WalletService walletService;
    private final TransactionRepository transactionRepository;
    private final RazorpayService razorpayService;
    private final UserRepository userRepository;
    private final RefundTokenService refundTokenService;
    private final NotificationService notificationService;
    private final RefundEmailService refundEmailService;

    @Transactional
    public void validateAndProcessRefund(Long invoiceId, Long merchantId, boolean approve) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        // 🔒 Verify merchant ownership
        if (!invoice.getMerchant().getId().equals(merchantId)) {
            throw new RuntimeException("Security violation: Merchant ID mismatch");
        }

        // 🔒 Ensure correct status for email approval
        if (invoice.getStatus() != InvoiceStatus.REFUND_REQUESTED) {
            throw new RuntimeException("Invoice is not in REFUND_REQUESTED state. Current status: " + invoice.getStatus());
        }

        if (approve) {
            // 🔒 Second-level check: Verify refund window again before processing
            if (invoice.getRefundWindowExpiry() == null ||
                    invoice.getRefundWindowExpiry().isBefore(LocalDateTime.now())) {
                throw new RuntimeException("Refund window expired. This transaction is no longer eligible for refund.");
            }
            refundInvoice(invoiceId);
        } else {
            rejectRefund(invoiceId);
        }
    }

    @Transactional
    public void requestRefund(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        if (invoice.getStatus() != InvoiceStatus.PAID) {
            throw new RuntimeException("Only paid invoices can be refunded");
        }

        // 🔒 Enforce refund window validation AT THE SOURCE
        if (invoice.getRefundWindowExpiry() != null &&
                invoice.getRefundWindowExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Refund window expired. This invoice is no longer eligible for a refund.");
        }

        invoice.setStatus(InvoiceStatus.REFUND_REQUESTED);
        invoiceRepository.save(invoice);

        // Notify Customer
        User customerUser = invoice.getCustomer().getUser();
        notificationService.createNotification(
                customerUser,
                "Refund request submitted for Invoice " + invoice.getInvoiceNumber(),
                NotificationType.INFO
        );

        // Notify Merchant
        User merchantUser = invoice.getMerchant().getUser();
        notificationService.createNotification(
                merchantUser,
                "Refund request received for Invoice " + invoice.getInvoiceNumber(),
                NotificationType.REFUND_REQUESTED
        );

        // Send Email
        String approveToken = refundTokenService.generateRefundToken(invoice.getId(), invoice.getMerchant().getId());
        String rejectToken = refundTokenService.generateRefundToken(invoice.getId(), invoice.getMerchant().getId());
        
        refundEmailService.sendRefundRequestEmail(
                merchantUser.getEmail(),
                invoice.getInvoiceNumber(),
                invoice.getCustomer() != null ? invoice.getCustomer().getName() : "Customer",
                invoice.getTotalPayable(),
                invoice.getPaymentMethod().name(),
                approveToken,
                rejectToken
        );
    }

    @Transactional
    public void rejectRefund(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        if (invoice.getStatus() != InvoiceStatus.REFUND_REQUESTED) {
            throw new RuntimeException("Invoice is not in refund requested status");
        }

        invoice.setStatus(InvoiceStatus.REFUND_REJECTED);
        invoiceRepository.save(invoice);
    }

    @Transactional
    public void refundInvoice(Long invoiceId) {

        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        // 🔒 Must be PAID or REFUND_REQUESTED
        if (invoice.getStatus() != InvoiceStatus.PAID && invoice.getStatus() != InvoiceStatus.REFUND_REQUESTED) {
            throw new RuntimeException("Only paid or refund-requested invoices can be refunded");
        }

        // 🔒 Must be inside refund window
        if (invoice.getRefundWindowExpiry() == null ||
                invoice.getRefundWindowExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Refund window expired");
        }

        BigDecimal amount = invoice.getTotalPayable();

        Wallet merchantWallet = walletService.getWalletByUser(
                invoice.getMerchant().getUser()
        );


        // 🔥 CASE 1 — Razorpay Payment (UPI_PAY or CARD)
        if (invoice.getPaymentMethod() == PaymentMethod.UPI_PAY || invoice.getPaymentMethod() == PaymentMethod.CARD) {

            // Call Razorpay refund API
            razorpayService.refundPayment(
                    invoice.getTransaction().getExternalReference(),
                    amount
            );

            // Debit merchant wallet
            walletService.debit(merchantWallet, amount, "REFUND-" + invoice.getInvoiceNumber());
        }

        // 🔥 CASE 2 — FACE_PAY
        else if (invoice.getPaymentMethod() == PaymentMethod.FACE_PAY) {
            
            if (invoice.getCustomer() == null) {
                throw new RuntimeException("FacePay invoice missing customer");
            }

            Wallet customerWallet = walletService.getWalletByUser(
                    invoice.getCustomer().getUser()
            );

            // Reverse internal wallet transfer
            String ref = "REFUND-FP-" + invoice.getInvoiceNumber();
            walletService.debit(merchantWallet, amount, ref);
            walletService.credit(customerWallet, amount, ref);
        }

        else {
            throw new RuntimeException("Unsupported payment method");
        }

        // 🧾 Create REFUND ledger entry
        Transaction refundTx = Transaction.builder()
                .senderWallet(merchantWallet)
                .receiverWallet(null)
                .invoice(invoice)   // ✅ ADD THIS
                .amount(amount)
                .transactionType(TransactionType.REFUND)
                .status(TransactionStatus.SUCCESS)
                .externalReference("REFUND-" + invoice.getId())
                .build();

        transactionRepository.save(refundTx);

        // 🔁 Update invoice status
        invoice.setStatus(InvoiceStatus.REFUNDED);
        invoiceRepository.save(invoice);

        // Notify Customer and Send Email
        if (invoice.getCustomer() != null && invoice.getCustomer().getUser() != null) {
            User customerUser = invoice.getCustomer().getUser();
            notificationService.createNotification(
                    customerUser,
                    "Refund completed for Invoice " + invoice.getInvoiceNumber(),
                    NotificationType.REFUND_COMPLETED
            );
            refundEmailService.sendRefundCompletedEmail(customerUser.getEmail(), invoice.getInvoiceNumber());
        }

        System.out.println("Refund processed for invoice: " + invoiceId);
    }
    @Transactional(readOnly = true)
    public List<MerchantRefundResponse> getMerchantRefundHistory(String merchantEmail) {

        User user = userRepository.findByEmail(merchantEmail)
                .orElseThrow(() -> new RuntimeException("Merchant not found"));

        if (user.getRole() != Role.MERCHANT) {
            throw new RuntimeException("Access denied");
        }

        Wallet wallet = walletService.getWalletByUser(user);

        return transactionRepository.findMerchantRefundHistory(wallet.getId());
    }

}