package com.billme.invoice;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CreateInvoiceRequest {

    private Long customerId;
    private BigDecimal amount;
}
