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
    private final PaymentSuccessEmailService paymentSuccessEmailService;

    @Transactional
    public void settlePayment(Invoice invoice, BigDecimal customerPayment, String paymentId) {

        // -----------------------------
        // Idempotency Protection
        // -----------------------------
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            return;
        }

        if (customerPayment == null) {
            throw new IllegalArgumentException("Customer payment amount missing");
        }

        // -----------------------------
        // Extract Invoice Financials
        // -----------------------------
        BigDecimal totalPayable = invoice.getTotalPayable();
        BigDecimal processingFee = invoice.getProcessingFee();

        if (totalPayable == null || processingFee == null) {
            throw new IllegalStateException("Invoice financial data corrupted");
        }

        BigDecimal merchantSettlement = totalPayable.subtract(processingFee);

        // -----------------------------
        // FINANCIAL RECONCILIATION GUARD
        // -----------------------------

        // 1️⃣ Customer must pay exact invoice total
        if (customerPayment.compareTo(totalPayable) != 0) {
            throw new IllegalStateException(
                    "Payment amount mismatch. Expected: "
                            + totalPayable
                            + " but received: "
                            + customerPayment
            );
        }

        // 2️⃣ Platform math must balance (Invariants)
        BigDecimal totalGst = (invoice.getCgstTotal() != null ? invoice.getCgstTotal() : BigDecimal.ZERO)
                .add(invoice.getSgstTotal() != null ? invoice.getSgstTotal() : BigDecimal.ZERO)
                .add(invoice.getIgstTotal() != null ? invoice.getIgstTotal() : BigDecimal.ZERO);

        BigDecimal expectedCustomerPayment = invoice.getSubtotal().add(totalGst).add(processingFee);

        if (expectedCustomerPayment.compareTo(customerPayment) != 0) {
            throw new IllegalStateException("Financial reconciliation failed: subtotal + tax + fee mismatch");
        }

        // -----------------------------
        // Merchant Wallet Settlement (TO ESCROW)
        // -----------------------------
        Wallet merchantWallet =
                walletService.getWalletByUser(invoice.getMerchant().getUser());

        walletService.creditEscrow(merchantWallet, merchantSettlement, paymentId);

        // -----------------------------
        // Ledger Transaction
        // -----------------------------
        Transaction ledgerTransaction = Transaction.builder()
                .senderWallet(null)
                .receiverWallet(merchantWallet)
                .amount(customerPayment) // what customer paid
                .invoiceAmount(totalPayable)
                .processingFee(processingFee)
                .merchantSettlement(merchantSettlement)
                .transactionType(TransactionType.INVOICE_PAYMENT)
                .status(TransactionStatus.SUCCESS)
                .externalReference(paymentId)
                .createdAt(LocalDateTime.now())
                .build();

        transactionRepository.save(ledgerTransaction);

        // -----------------------------
        // Update Invoice
        // -----------------------------
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setTransaction(ledgerTransaction);
        invoice.setPaidAt(LocalDateTime.now());

        // Release payment lock
        invoice.setPaymentInProgress(false);
        invoice.setPaymentStartedAt(null);

        // Refund window
        invoice.setRefundWindowExpiry(LocalDateTime.now().plusDays(3));

        invoiceRepository.save(invoice);

        // -----------------------------
        // Send Payment Success Email
        // -----------------------------
        paymentSuccessEmailService.sendPaymentSuccessEmail(invoice);
    }
}