package com.billme.invoice.dto;

import java.math.BigDecimal;

public class PublicInvoiceResponse {

    private String invoiceNumber;
    private String merchantName;
    private String customerName;
    private BigDecimal amount;
    private String status;

    public PublicInvoiceResponse(String invoiceNumber,
                                 String merchantName,
                                 String customerName,
                                 BigDecimal amount,
                                 String status) {
        this.invoiceNumber = invoiceNumber;
        this.merchantName = merchantName;
        this.customerName = customerName;
        this.amount = amount;
        this.status = status;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public String getCustomerName() {
        return customerName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getStatus() {
        return status;
    }
}