package com.billme.invoice.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class InvoiceItemResponse {

    private String productName;

    private BigDecimal unitPrice;

    private Integer quantity;

    private BigDecimal gstRate;

    private BigDecimal gstAmount;

    private BigDecimal totalPrice;
}