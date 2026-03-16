package com.billme.admin;

import com.billme.admin.dto.AdminFinancialSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminFinancialSummaryResponse> getSummary() {
        return ResponseEntity.ok(adminService.getFinancialSummary());
    }

    // ==========================================
    // ADMIN DASHBOARD
    // ==========================================
    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminFinancialSummaryResponse> getDashboard() {
        return ResponseEntity.ok(adminService.getFinancialSummary());
    }

    @GetMapping("/merchants")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<java.util.List<com.billme.merchant.MerchantProfile>> getMerchants() {
        return ResponseEntity.ok(adminService.getAllMerchants());
    }

    @GetMapping("/customers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<java.util.List<com.billme.customer.CustomerProfile>> getCustomers() {
        return ResponseEntity.ok(adminService.getAllCustomers());
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<java.util.List<com.billme.transaction.Transaction>> getTransactions() {
        return ResponseEntity.ok(adminService.getAllTransactions());
    }
}