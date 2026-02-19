package com.billme.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/customer/invoices")
@RequiredArgsConstructor
public class CustomerPaymentController {

    private final FacePayService facePayService;

    @PostMapping("/{id}/pay/face")
    public String payInvoice(@PathVariable Long id) {
        return facePayService.payInvoice(id);
    }
}
