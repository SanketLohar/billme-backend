package com.billme.invoice.dto;

import lombok.Data;

@Data
public class CreateInvoiceItemRequest {

    private Long productId;   // optional
    private String barcode;   // optional
    private Integer quantity;
}