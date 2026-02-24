package com.billme.payment;

import com.billme.invoice.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final InvoiceService invoiceService;

    @PostMapping("/create-order/{invoiceId}")
    public ResponseEntity<String> createOrder(
            @PathVariable Long invoiceId) {

        String orderId = invoiceService.createRazorpayOrder(invoiceId);

        return ResponseEntity.ok(orderId);
    }
}