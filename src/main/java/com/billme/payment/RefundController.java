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
    private final RefundTokenService refundTokenService;

    @PostMapping("/{invoiceId}")
    @PreAuthorize("hasRole('MERCHANT')")
    public String approveRefundMerchant(@PathVariable Long invoiceId) {
        refundService.refundInvoice(invoiceId);
        return "Refund successful";
    }

    @PostMapping("/reject/{invoiceId}")
    @PreAuthorize("hasRole('MERCHANT')")
    public String rejectRefundMerchant(@PathVariable Long invoiceId) {
        refundService.rejectRefund(invoiceId);
        return "Refund rejected";
    }

    @PostMapping("/request/{invoiceId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public String requestRefund(@PathVariable Long invoiceId) {
        refundService.requestRefund(invoiceId);
        return "Refund requested successfully";
    }

    @GetMapping("/email/approve/{token}")
    public String approveRefundEmail(@PathVariable String token) {
        if (!refundTokenService.isTokenValid(token)) {
            return "Invalid or expired link. Please request a new one.";
        }
        
        Long invoiceId = refundTokenService.extractInvoiceId(token);
        Long merchantId = refundTokenService.extractMerchantId(token);
        
        // Internal Validation and Status Check
        refundService.validateAndProcessRefund(invoiceId, merchantId, true);
        
        return "Refund approved successfully. You can close this tab.";
    }

    @GetMapping("/email/reject/{token}")
    public String rejectRefundEmail(@PathVariable String token) {
        if (!refundTokenService.isTokenValid(token)) {
            return "Invalid or expired link. Please request a new one.";
        }
        
        Long invoiceId = refundTokenService.extractInvoiceId(token);
        Long merchantId = refundTokenService.extractMerchantId(token);
        
        // Internal Validation and Status Check
        refundService.validateAndProcessRefund(invoiceId, merchantId, false);
        
        return "Refund rejected successfully. You can close this tab.";
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