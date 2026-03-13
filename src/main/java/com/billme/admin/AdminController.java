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
}