package com.billme.email;

import com.billme.invoice.Invoice;
import com.billme.invoice.InvoicePdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Service
@RequiredArgsConstructor
public class InvoiceEmailService {

    private final JavaMailSender mailSender;
    private final InvoicePdfService pdfService;

    public void sendInvoiceEmail(Invoice invoice) {

        try {

            byte[] pdf = pdfService.generateInvoicePdf(invoice);

            MimeMessage message = mailSender.createMimeMessage();

            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true);

            helper.setTo(invoice.getCustomer().getUser().getEmail());

            helper.setSubject("Invoice " + invoice.getInvoiceNumber());

            helper.setText("""
                    Hello,

                    You have received an invoice from %s.

                    Please find the invoice attached.

                    Thank you,
                    BillMe
                    """.formatted(invoice.getMerchant().getBusinessName()));

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