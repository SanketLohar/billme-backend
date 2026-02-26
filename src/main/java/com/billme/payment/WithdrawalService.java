package com.billme.payment;

import com.billme.payment.dto.WithdrawalResponse;
import com.billme.repository.TransactionRepository;
import com.billme.repository.UserRepository;
import com.billme.transaction.Transaction;
import com.billme.transaction.TransactionStatus;
import com.billme.transaction.TransactionType;
import com.billme.user.User;
import com.billme.wallet.Wallet;
import com.billme.wallet.WalletService;
import com.billme.wallet.dto.WalletSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WithdrawalService {

    private final WalletService walletService;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    @Value("${platform.withdrawal.fee-percent}")
    private BigDecimal withdrawalFeePercent;

    private static final BigDecimal MIN_WITHDRAWAL = BigDecimal.valueOf(100);

    // ============================================
    // WITHDRAW WITH PLATFORM FEE
    // ============================================
    @Transactional
    public void withdraw(BigDecimal amount) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Invalid withdrawal amount");
        }

        if (amount.compareTo(MIN_WITHDRAWAL) < 0) {
            throw new RuntimeException("Minimum withdrawal amount is ₹100");
        }

        User user = getLoggedInUser();
        Wallet merchantWallet = walletService.getWalletByUser(user);

        if (merchantWallet.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient wallet balance");
        }

        // 🔥 Calculate platform fee
        BigDecimal fee = amount
                .multiply(withdrawalFeePercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal netPayout = amount.subtract(fee);

        // 💸 Debit full amount from wallet
        walletService.debit(merchantWallet, amount);

        // 🧾 Ledger Entry 1: Withdrawal (gross amount)
        Transaction withdrawalTx = Transaction.builder()
                .senderWallet(merchantWallet)
                .receiverWallet(null) // External bank
                .amount(amount)
                .transactionType(TransactionType.WITHDRAWAL)
                .status(TransactionStatus.SUCCESS)
                .externalReference("SIMULATED-PAYOUT")
                .build();

        transactionRepository.save(withdrawalTx);

        // 🧾 Ledger Entry 2: Platform Fee
        Transaction feeTx = Transaction.builder()
                .senderWallet(merchantWallet)
                .receiverWallet(null) // Fee retained in platform Razorpay balance
                .amount(fee)
                .transactionType(TransactionType.PLATFORM_FEE)
                .status(TransactionStatus.SUCCESS)
                .externalReference("PLATFORM-FEE")
                .build();

        transactionRepository.save(feeTx);

        // 🏦 Simulated payout log
        System.out.println("🏦 Simulated payout to merchant bank: ₹" + netPayout);
        System.out.println("💼 Platform fee retained: ₹" + fee);
    }

    // ============================================
    // WITHDRAWAL HISTORY
    // ============================================
    @Transactional(readOnly = true)
    public List<WithdrawalResponse> getWithdrawalHistory() {

        User user = getLoggedInUser();
        Wallet wallet = walletService.getWalletByUser(user);

        return transactionRepository
                .findBySenderWalletAndTransactionTypeOrderByCreatedAtDesc(
                        wallet,
                        TransactionType.WITHDRAWAL
                )
                .stream()
                .map(tx -> WithdrawalResponse.builder()
                        .amount(tx.getAmount())
                        .status(tx.getStatus().name())
                        .createdAt(tx.getCreatedAt())
                        .reference(tx.getExternalReference())
                        .build()
                )
                .toList();
    }

    // ============================================
    // WALLET SUMMARY
    // ============================================
    @Transactional(readOnly = true)
    public WalletSummaryResponse getWalletSummary() {

        User user = getLoggedInUser();
        Wallet wallet = walletService.getWalletByUser(user);

        BigDecimal totalReceived = transactionRepository.getTotalReceived(
                wallet,
                TransactionType.INVOICE_PAYMENT
        );

        BigDecimal totalWithdrawn = transactionRepository.getTotalWithdrawn(
                wallet,
                TransactionType.WITHDRAWAL
        );

        BigDecimal totalPlatformFee = transactionRepository.getTotalWithdrawn(
                wallet,
                TransactionType.PLATFORM_FEE
        );

        return WalletSummaryResponse.builder()
                .currentBalance(wallet.getBalance())
                .totalReceived(totalReceived)
                .totalWithdrawn(totalWithdrawn)
                .platformFee(totalPlatformFee)   // 🔥 make sure DTO updated
                .build();
    }

    // ============================================
    // AUTH HELPER
    // ============================================
    private User getLoggedInUser() {

        String email = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}