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
        Wallet saved = walletRepository.save(wallet);

        ledgerService.record(saved.getId(), amount, LedgerEntryType.DEBIT, saved.getBalance(), referenceId);
    }

    @Transactional
    public void credit(Wallet wallet, BigDecimal amount, String referenceId) {
        wallet.setBalance(wallet.getBalance().add(amount));
        Wallet saved = walletRepository.save(wallet);

        ledgerService.record(saved.getId(), amount, LedgerEntryType.CREDIT, saved.getBalance(), referenceId);
    }

    @Transactional
    public void creditEscrow(Wallet wallet, BigDecimal amount, String referenceId) {
        wallet.setEscrowBalance(wallet.getEscrowBalance().add(amount));
        Wallet saved = walletRepository.save(wallet);

        ledgerService.record(saved.getId(), amount, LedgerEntryType.ESCROW_CREDIT, saved.getEscrowBalance(), referenceId);
    }

    @Transactional
    public void settleEscrowToBalance(Wallet wallet, BigDecimal amount, String referenceId) {
        if (wallet.getEscrowBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient escrow balance");
        }
        wallet.setEscrowBalance(wallet.getEscrowBalance().subtract(amount));
        wallet.setBalance(wallet.getBalance().add(amount));
        Wallet saved = walletRepository.save(wallet);

        // Record two entries: Escrow Debit (Settle) and Main Credit
        ledgerService.record(saved.getId(), amount, LedgerEntryType.ESCROW_SETTLE, saved.getEscrowBalance(), referenceId);
        ledgerService.record(saved.getId(), amount, LedgerEntryType.CREDIT, saved.getBalance(), referenceId);
    }
}
