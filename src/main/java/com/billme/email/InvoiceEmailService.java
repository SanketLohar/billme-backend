package com.billme.email;

import com.billme.invoice.Invoice;
import com.billme.invoice.InvoicePdfService;
import com.billme.invoice.InvoiceTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;

@Service
@RequiredArgsConstructor
public class InvoiceEmailService {

    private final JavaMailSender mailSender;
    private final InvoicePdfService pdfService;
    private final InvoiceTemplateService templateService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Async
    public void sendInvoiceEmail(Invoice invoice) {

        try {

            // Generate HTML invoice (used for preview if needed)
            String html = templateService.generateInvoiceHtml(invoice);

            // Generate PDF directly from invoice
            byte[] pdf = pdfService.generateInvoicePdf(invoice);

            MimeMessage message = mailSender.createMimeMessage();

            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(invoice.getResolvedCustomerEmail());

            helper.setSubject(
                    "Invoice " +
                            invoice.getInvoiceNumber() +
                            " from " +
                            invoice.getMerchant().getBusinessName()
            );

            // Secure Pay Now link
            String payLink = frontendUrl
                    + "/pay-invoice.html?invoice="
                    + invoice.getInvoiceNumber()
                    + "&token="
                    + invoice.getPaymentToken();

            String htmlContent =
                    "<div style='font-family: Arial, sans-serif; max-width:600px;margin:auto'>" +
                            "<h2 style='color:#1a73e8'>BillMe</h2>" +
                            "<p>You have received an invoice from <b>" +
                            invoice.getMerchant().getBusinessName() +
                            "</b></p>" +
                            "<p><b>Invoice Number:</b> " + invoice.getInvoiceNumber() + "</p>" +
                            "<p><b>Amount Due:</b> ₹" + invoice.getTotalPayable() + "</p>" +
                            "<br>" +
                            "<a href='" + payLink + "' " +
                            "style='padding:12px 24px;background:#1a73e8;color:white;text-decoration:none;border-radius:6px'>" +
                            "Pay Now</a>" +
                            "<br><br>" +
                            "<p>Please find the invoice attached.</p>" +
                            "</div>";

            helper.setText(htmlContent, true);

            helper.addAttachment(
                    "invoice-" + invoice.getInvoiceNumber() + ".pdf",
                    () -> new ByteArrayInputStream(pdf)
            );

            mailSender.send(message);

        } catch (Exception e) {
            throw new RuntimeException("Failed to send invoice email", e);
        }
    }
}