package com.billme.invoice;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class InvoicePdfService {

    private final InvoiceTemplateService invoiceTemplateService;

    public byte[] generateInvoicePdf(Invoice invoice) {

        try {

            String html = invoiceTemplateService.generateInvoiceHtml(invoice);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, null);

            ClassPathResource fontResource =
                    new ClassPathResource("fonts/NotoSans-Regular.ttf");

            try (InputStream fontStream = fontResource.getInputStream()) {

                builder.useFont(() -> fontStream, "NotoSans");

                builder.toStream(outputStream);
                builder.run();
            }

            return outputStream.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate invoice PDF", e);
        }
    }
}