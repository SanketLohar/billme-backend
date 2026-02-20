package com.billme.invoice;

import com.billme.invoice.dto.CreateInvoiceItemRequest;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateInvoiceRequest {

    private Long customerId;

    private List<CreateInvoiceItemRequest> items;
}
