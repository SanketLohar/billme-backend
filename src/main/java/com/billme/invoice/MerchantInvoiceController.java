package com.billme.invoice;

import com.billme.invoice.CreateInvoiceRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/merchant/invoices")
@RequiredArgsConstructor
public class MerchantInvoiceController {

    private final InvoiceService invoiceService;

    @PostMapping
    public ResponseEntity<String> createInvoice(
            @RequestBody CreateInvoiceRequest request,
            Authentication authentication
    ) {

        invoiceService.createInvoice(request, authentication.getName());

        return ResponseEntity.ok("Invoice created successfully");
    }
}