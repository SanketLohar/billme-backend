package com.billme.invoice.dto;

import lombok.Data;

@Data
public class CreateInvoiceItemRequest {

    private Long productId;
    private Integer quantity;
}