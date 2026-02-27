package com.billme.admin;

import com.billme.admin.dto.AdminFinancialSummaryResponse;
import com.billme.repository.InvoiceRepository;
import com.billme.repository.TransactionRepository;
import com.billme.transaction.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final TransactionRepository transactionRepository;
    private final InvoiceRepository invoiceRepository;

    @Transactional(readOnly = true)
    public AdminFinancialSummaryResponse getFinancialSummary() {

        BigDecimal totalRevenue =
                transactionRepository.sumByTransactionType(TransactionType.INVOICE_PAYMENT);

        BigDecimal totalPlatformFees =
                transactionRepository.sumByTransactionType(TransactionType.PLATFORM_FEE);

        BigDecimal totalRefundAmount =
                transactionRepository.sumByTransactionType(TransactionType.REFUND);

        BigDecimal totalWithdrawals =
                transactionRepository.sumByTransactionType(TransactionType.WITHDRAWAL);

        BigDecimal totalLockedAmount =
                invoiceRepository.sumLockedAmount();

        return new AdminFinancialSummaryResponse(
                totalRevenue,
                totalPlatformFees,
                totalRefundAmount,
                totalWithdrawals,
                totalLockedAmount
        );
    }
}