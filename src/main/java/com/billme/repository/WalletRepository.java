package com.billme.repository;

import com.billme.wallet.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletRepository
        extends JpaRepository<Wallet, Long> {
}
