package com.billme.payment;

import com.billme.repository.TransactionRepository;
import com.billme.transaction.*;
import com.billme.wallet.Wallet;
import com.billme.wallet.WalletService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FacePayService {

    private final WalletService walletService;
    private final TransactionRepository transactionRepository;

    @Transactional
    public Transaction transfer(Wallet sender, Wallet receiver, BigDecimal amount) {

        // 1️⃣ Debit sender
        walletService.debit(sender, amount);

        // 2️⃣ Credit receiver
        walletService.credit(receiver, amount);

        // 3️⃣ Save transaction record
        Transaction transaction = Transaction.builder()
                .senderWallet(sender)
                .receiverWallet(receiver)
                .amount(amount)
                .transactionType(TransactionType.FACE_PAY)
                .status(TransactionStatus.SUCCESS)
                .createdAt(LocalDateTime.now())
                .build();

        return transactionRepository.save(transaction);
    }
}
