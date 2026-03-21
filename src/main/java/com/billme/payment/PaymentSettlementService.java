package com.billme.payment;

import com.billme.email.PaymentSuccessEmailService;
import com.billme.invoice.Invoice;
import com.billme.invoice.InvoiceStatus;
import com.billme.repository.InvoiceRepository;
import com.billme.repository.TransactionRepository;
import com.billme.transaction.Transaction;
import com.billme.transaction.TransactionStatus;
import com.billme.transaction.TransactionType;
import com.billme.wallet.Wallet;
import com.billme.notification.NotificationService;
import com.billme.wallet.WalletService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentSettlementService {

    private final WalletService walletService;
    private final TransactionRepository transactionRepository;
    private final InvoiceRepository invoiceRepository;
    private final NotificationService notificationService;

    @Transactional
    public void settlePayment(Invoice invoice, BigDecimal customerPayment, String paymentId) {

        // -----------------------------
        // 1. Idempotency Protection
        // -----------------------------
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            System.out.println("⚠️ [IDEMPOTENCY] Invoice " + invoice.getInvoiceNumber() + " already paid. Skipping.");
            return;
        }

        if (customerPayment == null) {
            throw new IllegalArgumentException("Customer payment amount missing");
        }

        // -----------------------------
        // 2. Financial Reconciliation Guard
        // -----------------------------
        BigDecimal totalPayable = invoice.getTotalPayable();
        BigDecimal processingFee = invoice.getProcessingFee();

        if (totalPayable == null || processingFee == null) {
            throw new IllegalStateException("Invoice financial data corrupted");
        }

        if (customerPayment.compareTo(totalPayable) != 0) {
            throw new IllegalStateException("Payment mismatch. Expected: " + totalPayable + " Got: " + customerPayment);
        }

        BigDecimal merchantSettlement = totalPayable.subtract(processingFee);

        // -----------------------------
        // 3. Atomic Wallet Transfer (Customer -> Merchant Escrow)
        // -----------------------------
        // This call is atomic and protected by pessimistic locks inside transferFunds
        walletService.transferFunds(
                invoice.getCustomer().getUser(),
                invoice.getMerchant().getUser(),
                customerPayment,
                paymentId
        );

        // -----------------------------
        // 4. Ledger Transaction & Invoice Update
        // -----------------------------
        Transaction ledgerTransaction = Transaction.builder()
                .senderWallet(null) // Generic ledger for now
                .receiverWallet(null)
                .amount(customerPayment)
                .invoiceAmount(totalPayable)
                .processingFee(processingFee)
                .merchantSettlement(merchantSettlement)
                .transactionType(TransactionType.INVOICE_PAYMENT)
                .status(TransactionStatus.SUCCESS)
                .externalReference(paymentId)
                .createdAt(LocalDateTime.now())
                .build();

        transactionRepository.save(ledgerTransaction);

        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setTransaction(ledgerTransaction);
        invoice.setPaidAt(LocalDateTime.now());
        invoice.setPaymentInProgress(false);
        invoice.setRefundWindowExpiry(LocalDateTime.now().plusDays(3));

        invoiceRepository.save(invoice);

        System.out.println("✅ [PAYMENT SETTLED] Invoice: " + invoice.getInvoiceNumber() + " Ref: " + paymentId);

        // -----------------------------
        // 5. Trigger Notifications
        // -----------------------------
        // notificationService will handle in-app and async emails
        notificationService.sendPaymentNotifications(invoice);
    }
}