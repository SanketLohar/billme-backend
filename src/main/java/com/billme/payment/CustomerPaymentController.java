package com.billme.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/customer/invoices")
@RequiredArgsConstructor
public class CustomerPaymentController {

    private final FacePayService facePayService;

    @PostMapping("/{id}/pay/face")
    public ResponseEntity<String> payInvoice(
            @PathVariable Long id,
            @RequestBody FacePayRequest request) {

        String result = facePayService.payInvoice(id, request.getEmbedding());
        return ResponseEntity.ok(result);
    }
}
