package com.billme.invoice;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class CustomerInvoiceResponse {

    private Long id;
    private String invoiceNumber;
    private String merchantName;
    private BigDecimal amount;
    private InvoiceStatus status;
    private LocalDateTime issuedAt;
    private LocalDateTime paidAt;
}
