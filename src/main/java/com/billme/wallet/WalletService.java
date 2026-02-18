package com.billme.wallet;

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

    public Wallet getWalletByUser(User user) {
        return walletRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Wallet not found"));
    }


    @Transactional
    public void debit(Wallet wallet, BigDecimal amount) {

        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient balance");
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));
        walletRepository.save(wallet);
    }

    @Transactional
    public void credit(Wallet wallet, BigDecimal amount) {
        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);
    }
}
