package com.billme.payment;

import com.billme.payment.dto.MerchantRefundResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @GetMapping("/merchant/refunds")
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<List<MerchantRefundResponse>> getMerchantRefundHistory(
            Authentication authentication
    ) {
        String email = authentication.getName();
        List<MerchantRefundResponse> refunds =
                refundService.getMerchantRefundHistory(email);

        return ResponseEntity.ok(refunds);
    }
}