package com.billme.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/refund")
public class RefundController {

    private final RefundService refundService;

    @PostMapping("/{invoiceId}")
    public String refund(@PathVariable Long invoiceId) {
        refundService.refundInvoice(invoiceId);
        return "Refund successful";
    }
}