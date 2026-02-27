package com.billme.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class AdminFinancialSummaryResponse {

    private BigDecimal totalRevenue;

    private BigDecimal totalPlatformFees;

    private BigDecimal totalRefundAmount;

    private BigDecimal totalWithdrawals;

    private BigDecimal totalLockedAmount;
}