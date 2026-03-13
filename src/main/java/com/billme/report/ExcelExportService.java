package com.billme.report;

import com.billme.report.dto.ReportTransactionResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExcelExportService {

    public byte[] exportToExcel(List<ReportTransactionResponse> transactions) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Transactions");

            // Header
            Row headerRow = sheet.createRow(0);
            String[] columns = {"Invoice Number", "Customer", "Amount", "GST", "Payment Method", "Date"};
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
            }

            // Data
            int rowIdx = 1;
            for (ReportTransactionResponse txn : transactions) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(txn.getInvoiceNumber());
                row.createCell(1).setCellValue(txn.getCustomerName());
                row.createCell(2).setCellValue(txn.getAmount() != null ? txn.getAmount().doubleValue() : 0);
                row.createCell(3).setCellValue(txn.getGst() != null ? txn.getGst().doubleValue() : 0);
                row.createCell(4).setCellValue(txn.getPaymentMethod());
                row.createCell(5).setCellValue(txn.getDate() != null ? txn.getDate().toString() : "");
            }

            workbook.write(out);
            return out.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Failed to generate Excel file", e);
        }
    }
}
