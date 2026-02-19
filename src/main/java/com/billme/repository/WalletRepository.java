package com.billme.repository;

import com.billme.wallet.Wallet;
import com.billme.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    // Fetch by User entity
    Optional<Wallet> findByUser(User user);

    // 🔥 Recommended for services
    Optional<Wallet> findByUser_Id(Long userId);
}
