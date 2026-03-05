package com.billme.invoice;

import com.billme.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/invoice")
public class InvoicePdfController {

    private final InvoiceRepository invoiceRepository;
    private final InvoicePdfService pdfService;
    private final InvoiceTemplateService templateService; // 👈 ADD THIS

    @Transactional(readOnly = true)
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadInvoicePdf(@PathVariable Long id) {

        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        // Generate HTML
        String html = templateService.generateInvoiceHtml(invoice);

        // Convert HTML → PDF
        byte[] pdf = pdfService.generatePdf(html);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=invoice-" + invoice.getInvoiceNumber() + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}