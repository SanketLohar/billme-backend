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
    private final WalletRepository walletRepository;
    @Transactional
    public void refundInvoice(Long invoiceId) {

        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        // 🔒 Must be PAID
        if (invoice.getStatus() != InvoiceStatus.PAID) {
            throw new RuntimeException("Only paid invoices can be refunded");
        }

        // 🔒 Must be inside refund window
        if (invoice.getRefundWindowExpiry() == null ||
                invoice.getRefundWindowExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Refund window expired");
        }

        BigDecimal amount = invoice.getAmount();

        Wallet merchantWallet = walletService.getWalletByUser(
                invoice.getMerchant().getUser()
        );


        // 🔥 CASE 1 — Razorpay Payment
        if (invoice.getPaymentMethod() == PaymentMethod.UPI_PAY) {

            // Call Razorpay refund API
            razorpayService.refundPayment(
                    invoice.getTransaction().getExternalReference(),
                    amount
            );

            // Debit merchant wallet
            walletService.debit(merchantWallet, amount);
        }

        // 🔥 CASE 2 — FACE_PAY
        else if (invoice.getPaymentMethod() == PaymentMethod.FACE_PAY) {

            Wallet customerWallet = walletService.getWalletByUser(
                    invoice.getCustomer().getUser()
            );

            // Reverse internal wallet transfer
            walletService.debit(merchantWallet, amount);
            walletService.credit(customerWallet, amount);
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