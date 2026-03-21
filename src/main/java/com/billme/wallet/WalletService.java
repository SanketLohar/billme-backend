package com.billme.wallet;

import com.billme.transaction.LedgerEntryType;
import com.billme.transaction.LedgerService;
import com.billme.repository.WalletRepository;
import com.billme.user.User;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final LedgerService ledgerService;

    public Wallet getWalletByUser(User user) {
        return walletRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Wallet not found"));
    }

    @Transactional
    public void debit(Wallet wallet, BigDecimal amount, String referenceId) {
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient balance");
        }
        wallet.setBalance(wallet.getBalance().subtract(amount));
        walletRepository.save(wallet);
        ledgerService.record(wallet.getId(), amount, LedgerEntryType.DEBIT, wallet.getBalance(), referenceId);
    }

    @Transactional
    public void credit(Wallet wallet, BigDecimal amount, String referenceId) {
        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);
        ledgerService.record(wallet.getId(), amount, LedgerEntryType.CREDIT, wallet.getBalance(), referenceId);
    }

    @Transactional
    public void creditEscrow(Wallet wallet, BigDecimal amount, String referenceId) {
        wallet.setEscrowBalance(wallet.getEscrowBalance().add(amount));
        walletRepository.save(wallet);
        ledgerService.record(wallet.getId(), amount, LedgerEntryType.ESCROW_CREDIT, wallet.getEscrowBalance(), referenceId);
    }

    @Transactional
    public void settleEscrowToBalance(Wallet wallet, BigDecimal amount, String referenceId) {
        if (wallet.getEscrowBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient escrow balance");
        }
        wallet.setEscrowBalance(wallet.getEscrowBalance().subtract(amount));
        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);
        ledgerService.record(wallet.getId(), amount, LedgerEntryType.ESCROW_SETTLE, wallet.getEscrowBalance(), referenceId);
        ledgerService.record(wallet.getId(), amount, LedgerEntryType.CREDIT, wallet.getBalance(), referenceId);
    }

    @Transactional
    public void transferFunds(User sender, User receiver, BigDecimal amount, String referenceId) {
        // 1. Fetch wallets with Pessimistic Lock for concurrency safety
        Wallet senderWallet = walletRepository.findByUserWithLock(sender)
                .orElseThrow(() -> new RuntimeException("Sender wallet not found"));
        Wallet receiverWallet = walletRepository.findByUserWithLock(receiver)
                .orElseThrow(() -> new RuntimeException("Receiver wallet not found"));

        // 2. Strict Balance Validation
        if (senderWallet.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient balance");
        }

        // 3. Debit Customer (Main Balance)
        senderWallet.setBalance(senderWallet.getBalance().subtract(amount));
        walletRepository.save(senderWallet);
        ledgerService.record(senderWallet.getId(), amount, LedgerEntryType.DEBIT, senderWallet.getBalance(), referenceId);
        System.out.println("✅ [WALLET DEBIT] Success: Amount ₹" + amount + " for WalletID " + senderWallet.getId() + " Ref: " + referenceId);

        // 4. Credit Merchant (Escrow Balance)
        receiverWallet.setEscrowBalance(receiverWallet.getEscrowBalance().add(amount));
        walletRepository.save(receiverWallet);
        ledgerService.record(receiverWallet.getId(), amount, LedgerEntryType.ESCROW_CREDIT, receiverWallet.getEscrowBalance(), referenceId);
        System.out.println("✅ [WALLET CREDIT] Success: Amount ₹" + amount + " to Escrow for WalletID " + receiverWallet.getId() + " Ref: " + referenceId);
    }
}
