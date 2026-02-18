package com.billme.transaction;

import com.billme.wallet.Wallet;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Sender Wallet
    @ManyToOne
    @JoinColumn(name = "sender_wallet_id")
    private Wallet senderWallet;

    // Receiver Wallet (nullable for UPI external)
    @ManyToOne
    @JoinColumn(name = "receiver_wallet_id")
    private Wallet receiverWallet;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    private String externalReference; // for UPI txn id

    private LocalDateTime createdAt;
}
