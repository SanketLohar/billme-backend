package com.billme.invoice;

import com.billme.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/invoice")
public class InvoicePreviewController {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceTemplateService invoiceTemplateService;

    @Transactional(readOnly = true)
    @GetMapping(value = "/{id}/preview", produces = MediaType.TEXT_HTML_VALUE)
    public String previewInvoice(@PathVariable Long id) {

        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        return invoiceTemplateService.renderInvoiceHtml(invoice);
    }
}