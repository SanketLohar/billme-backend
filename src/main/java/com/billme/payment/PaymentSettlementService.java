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
    public void settlePayment(Invoice invoice,
                              BigDecimal amount,
                              String paymentId) {

        // Idempotency protection
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            return;
        }

        Wallet merchantWallet =
                walletService.getWalletByUser(invoice.getMerchant().getUser());

        // Credit merchant wallet
        walletService.credit(merchantWallet, amount);

        // Create ledger transaction
        Transaction ledgerTransaction = Transaction.builder()
                .senderWallet(null)
                .receiverWallet(merchantWallet)
                .amount(amount)
                .transactionType(TransactionType.INVOICE_PAYMENT)
                .status(TransactionStatus.SUCCESS)
                .externalReference(paymentId)
                .createdAt(LocalDateTime.now())
                .build();

        transactionRepository.save(ledgerTransaction);

        // Update invoice
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setTransaction(ledgerTransaction);
        invoice.setPaidAt(LocalDateTime.now());

        // release payment lock
        invoice.setPaymentInProgress(false);
        invoice.setPaymentStartedAt(null);

        // refund window
        invoice.setRefundWindowExpiry(LocalDateTime.now().plusDays(3));

        invoiceRepository.save(invoice);

        // send email
        paymentSuccessEmailService.sendPaymentSuccessEmail(invoice);
    }
}