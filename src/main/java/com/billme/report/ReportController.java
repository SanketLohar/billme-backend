package com.billme.report;

import com.billme.merchant.MerchantProfile;
import com.billme.report.dto.BalanceSheetResponse;
import com.billme.report.dto.ReportTransactionResponse;
import com.billme.repository.MerchantProfileRepository;
import com.billme.repository.UserRepository;
import com.billme.user.User;
import com.billme.email.ReportEmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/merchant/reports")
@RequiredArgsConstructor
public class ReportController {

    private final BalanceSheetService balanceSheetService;
    private final ReportService reportService;
    private final ExcelExportService excelExportService;
    private final ReportEmailService reportEmailService;
    private final UserRepository userRepository;
    private final MerchantProfileRepository merchantProfileRepository;

    private MerchantProfile getLoggedInMerchant() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return merchantProfileRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new RuntimeException("Merchant profile not found"));
    }

    @GetMapping("/balance-sheet")
    public ResponseEntity<BalanceSheetResponse> getBalanceSheet() {
        return ResponseEntity.ok(balanceSheetService.generateBalanceSheet(getLoggedInMerchant()));
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<ReportTransactionResponse>> getTransactions() {
        return ResponseEntity.ok(reportService.getTransactions(getLoggedInMerchant()));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportTransactionsToExcel() {
        List<ReportTransactionResponse> transactions = reportService.getTransactions(getLoggedInMerchant());
        byte[] excelFile = excelExportService.exportToExcel(transactions);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=transactions_report.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelFile);
    }

    @PostMapping("/email")
    public ResponseEntity<String> sendReportViaEmail() {
        MerchantProfile merchant = getLoggedInMerchant();
        List<ReportTransactionResponse> transactions = reportService.getTransactions(merchant);
        byte[] excelFile = excelExportService.exportToExcel(transactions);
        
        reportEmailService.sendReportEmail(merchant, excelFile);
        
        return ResponseEntity.ok("Report email initiated successfully");
    }
}
