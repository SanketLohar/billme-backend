package com.billme.payment;

import com.billme.invoice.Invoice;
import com.billme.invoice.InvoiceStatus;
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

@Service
@RequiredArgsConstructor
public class PaymentSettlementService {

    private final WalletService walletService;
    private final TransactionRepository transactionRepository;

    @Transactional
    public void settlePayment(Invoice invoice,
                              BigDecimal amount,
                              String paymentId) {

        // 🔒 Idempotency Guard
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            System.out.println("Settlement skipped - invoice already PAID");
            return;
        }

        // 1️⃣ Get merchant wallet
        Wallet merchantWallet =
                walletService.getWalletByUser(
                        invoice.getMerchant().getUser()
                );

        // 2️⃣ Credit merchant wallet
        walletService.credit(merchantWallet, amount);

        // 3️⃣ Create ledger transaction
        Transaction ledgerTransaction = Transaction.builder()
                .senderWallet(null) // External source (Razorpay)
                .receiverWallet(merchantWallet)
                .amount(amount)
                .transactionType(TransactionType.INVOICE_PAYMENT)
                .status(TransactionStatus.SUCCESS)
                .externalReference(paymentId)
                .build();

        transactionRepository.save(ledgerTransaction);

        // 4️⃣ Mark invoice PAID (ONLY here)
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setTransaction(ledgerTransaction);

        System.out.println("💰 Merchant wallet credited & invoice settled");
    }
}