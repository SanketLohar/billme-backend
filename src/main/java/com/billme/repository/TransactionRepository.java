package com.billme.repository;

import com.billme.payment.dto.MerchantRefundResponse;
import com.billme.transaction.Transaction;
import com.billme.transaction.TransactionStatus;
import com.billme.transaction.TransactionType;
import com.billme.wallet.Wallet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("""
        SELECT t FROM Transaction t
        WHERE 
            (t.senderWallet.user.id = :userId 
             OR t.receiverWallet.user.id = :userId)
        AND (:type IS NULL OR t.transactionType = :type)
        AND (:status IS NULL OR t.status = :status)
        AND (:fromDate IS NULL OR t.createdAt >= :fromDate)
        AND (:toDate IS NULL OR t.createdAt <= :toDate)
        ORDER BY t.createdAt DESC
    """)
    Page<Transaction> findUserTransactions(
            @Param("userId") Long userId,
            @Param("type") TransactionType type,
            @Param("status") TransactionStatus status,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            Pageable pageable
    );

    List<Transaction> findBySenderWalletAndTransactionTypeOrderByCreatedAtDesc(
            Wallet wallet,
            TransactionType type
    );

    @Query("""
        SELECT COALESCE(SUM(t.amount), 0)
        FROM Transaction t
        WHERE t.receiverWallet = :wallet
        AND t.transactionType = :type
    """)
    BigDecimal getTotalReceived(
            @Param("wallet") Wallet wallet,
            @Param("type") TransactionType type
    );

    @Query("""
        SELECT COALESCE(SUM(t.amount), 0)
        FROM Transaction t
        WHERE t.senderWallet = :wallet
        AND t.transactionType = :type
    """)
    BigDecimal getTotalWithdrawn(
            @Param("wallet") Wallet wallet,
            @Param("type") TransactionType type
    );

    @Query("""
       SELECT new com.billme.payment.dto.MerchantRefundResponse(
            t.invoice.invoiceNumber,
            t.amount,
            t.invoice.paymentMethod,
            t.createdAt,
            t.externalReference
       )
       FROM Transaction t
       WHERE t.senderWallet.id = :walletId
         AND t.transactionType = com.billme.transaction.TransactionType.REFUND
         AND t.status = com.billme.transaction.TransactionStatus.SUCCESS
       ORDER BY t.createdAt DESC
       """)
    List<MerchantRefundResponse> findMerchantRefundHistory(@Param("walletId") Long walletId);

    @Query("""
       SELECT COALESCE(SUM(t.amount), 0)
       FROM Transaction t
       WHERE t.transactionType = :type
       AND t.status = com.billme.transaction.TransactionStatus.SUCCESS
       """)
    BigDecimal sumByTransactionType(@Param("type") TransactionType type);

}