package com.billme.repository;

import com.billme.wallet.Wallet;
import com.billme.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByUser(User user);

}
