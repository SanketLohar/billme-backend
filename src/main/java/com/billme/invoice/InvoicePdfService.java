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

    public byte[] generatePdf(String html) {

        try {

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, null);

            // Load rupee-compatible font
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