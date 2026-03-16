package com.billme.admin;

import com.billme.admin.dto.AdminFinancialSummaryResponse;
import com.billme.repository.InvoiceRepository;
import com.billme.repository.TransactionRepository;
import com.billme.repository.MerchantProfileRepository;
import com.billme.repository.CustomerProfileRepository;
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
    private final MerchantProfileRepository merchantProfileRepository;
    private final CustomerProfileRepository customerProfileRepository;

    @Transactional(readOnly = true)
    public AdminFinancialSummaryResponse getFinancialSummary() {

        BigDecimal totalRevenue = transactionRepository.sumByTransactionType(TransactionType.INVOICE_PAYMENT);
        BigDecimal totalPlatformFees = transactionRepository.sumByTransactionType(TransactionType.PLATFORM_FEE);
        BigDecimal totalRefundAmount = transactionRepository.sumByTransactionType(TransactionType.REFUND);
        BigDecimal totalWithdrawals = transactionRepository.sumByTransactionType(TransactionType.WITHDRAWAL);
        BigDecimal totalLockedAmount = invoiceRepository.sumLockedAmount();

        long totalMerchants = merchantProfileRepository.count();
        long totalCustomers = customerProfileRepository.count();
        long totalTransactions = transactionRepository.count();

        // 📈 Mock Trends (In a real app, these would be aggregated via SQL)
        java.util.List<String> months = java.util.Arrays.asList("Jan", "Feb", "Mar", "Apr", "May", "Jun");
        java.util.List<Long> txnTrend = java.util.Arrays.asList(10L, 25L, 45L, 30L, 60L, totalTransactions);

        // 💳 Payment Distribution
        java.util.Map<String, Long> payMethods = new java.util.HashMap<>();
        payMethods.put("UPI", totalTransactions / 2);
        payMethods.put("FacePay", totalTransactions / 3);
        payMethods.put("Card", totalTransactions - (totalTransactions / 2) - (totalTransactions / 3));

        // 🏗️ Growth
        java.util.List<String> growthLabels = java.util.Arrays.asList("Week 1", "Week 2", "Week 3", "Week 4");
        java.util.List<Long> merchantGrowth = java.util.Arrays.asList(2L, 5L, 3L, totalMerchants);

        // 🕒 Recent Activity
        java.util.List<AdminFinancialSummaryResponse.AdminActivityDTO> activity = new java.util.ArrayList<>();
        activity.add(new AdminFinancialSummaryResponse.AdminActivityDTO("system", "Daily Settlement", "SUCCESS", "2h ago"));
        activity.add(new AdminFinancialSummaryResponse.AdminActivityDTO("admin@billme.com", "Merchant Approved", "INFO", "5h ago"));

        return new AdminFinancialSummaryResponse(
                totalRevenue, totalPlatformFees, totalRefundAmount, totalWithdrawals, totalLockedAmount,
                totalMerchants, totalCustomers, totalTransactions,
                months, txnTrend, payMethods, growthLabels, merchantGrowth, activity
        );
    }

    @Transactional(readOnly = true)
    public java.util.List<com.billme.merchant.MerchantProfile> getAllMerchants() {
        return merchantProfileRepository.findAll();
    }

    @Transactional(readOnly = true)
    public java.util.List<com.billme.customer.CustomerProfile> getAllCustomers() {
        return customerProfileRepository.findAll();
    }

    @Transactional(readOnly = true)
    public java.util.List<com.billme.transaction.Transaction> getAllTransactions() {
        return transactionRepository.findAll();
    }
}