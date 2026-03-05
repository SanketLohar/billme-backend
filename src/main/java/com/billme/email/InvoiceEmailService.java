package com.billme.email;

import com.billme.invoice.Invoice;
import com.billme.invoice.InvoicePdfService;
import com.billme.invoice.InvoiceTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Service
@RequiredArgsConstructor
public class InvoiceEmailService {

    private final JavaMailSender mailSender;
    private final InvoicePdfService pdfService;
    private final InvoiceTemplateService templateService;

    private final String frontendUrl = "http://localhost:5173"; // change later

    @Async
    public void sendInvoiceEmail(Invoice invoice) {

        try {

            // Generate HTML invoice
            String html = templateService.generateInvoiceHtml(invoice);

            // Convert to PDF
            byte[] pdf = pdfService.generatePdf(html);

            MimeMessage message = mailSender.createMimeMessage();

            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true);

            helper.setTo(invoice.getCustomer().getUser().getEmail());

            helper.setSubject("Invoice " + invoice.getInvoiceNumber());

            // 🔐 Secure Pay Now Link
            String payLink = frontendUrl
                    + "/pay/invoice/"
                    + invoice.getInvoiceNumber()
                    + "?token="
                    + invoice.getPaymentToken();

            helper.setText("""
                    Hello,

                    You have received an invoice from %s.

                    Amount Due: ₹%s

                    Pay your invoice securely using the link below:

                    %s

                    Please find the invoice attached.

                    Thank you,
                    BillMe
                    """.formatted(
                    invoice.getMerchant().getBusinessName(),
                    invoice.getTotalPayable(),
                    payLink
            ));

            helper.addAttachment(
                    "invoice-" + invoice.getInvoiceNumber() + ".pdf",
                    () -> new java.io.ByteArrayInputStream(pdf)
            );

            mailSender.send(message);

        } catch (Exception e) {
            throw new RuntimeException("Failed to send invoice email", e);
        }
    }
}