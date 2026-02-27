package com.billme.payment;

import com.billme.invoice.Invoice;
import com.billme.invoice.InvoiceStatus;
import com.billme.invoice.PaymentMethod;
import com.billme.repository.TransactionRepository;
import com.billme.transaction.Transaction;
import com.billme.transaction.TransactionStatus;
import com.billme.transaction.TransactionType;
import com.billme.wallet.Wallet;
import com.billme.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import com.billme.repository.InvoiceRepository;
@Service
@RequiredArgsConstructor
public class PaymentSettlementService {

    private final WalletService walletService;
    private final TransactionRepository transactionRepository;
    private final InvoiceRepository invoiceRepository;
    @Transactional
    public void settlePayment(Invoice invoice,
                              BigDecimal amount,
                              String paymentId) {

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            return;
        }

        Wallet merchantWallet =
                walletService.getWalletByUser(invoice.getMerchant().getUser());

        walletService.credit(merchantWallet, amount);

        Transaction ledgerTransaction = Transaction.builder()
                .senderWallet(null)
                .receiverWallet(merchantWallet)
                .amount(amount)
                .transactionType(TransactionType.INVOICE_PAYMENT)
                .status(TransactionStatus.SUCCESS)
                .externalReference(paymentId)
                .build();

        transactionRepository.save(ledgerTransaction);

        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setTransaction(ledgerTransaction);
        invoice.setPaidAt(LocalDateTime.now());
        invoice.setRefundWindowExpiry(LocalDateTime.now().plusDays(3));

        invoiceRepository.save(invoice);
    }
}