package com.billme.email;

import com.billme.invoice.Invoice;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;

    @Override
    public void sendEmail(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }

    @Override
    @Async
    public void sendCustomerPaymentSuccessEmail(Invoice invoice) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setTo(invoice.getResolvedCustomerEmail());
            helper.setSubject("Payment Success - BillMe Invoice #" + invoice.getInvoiceNumber());

            String content = String.format("""
                <html>
                <body style="font-family: Arial, sans-serif; color: #333;">
                    <div style="max-width: 600px; margin: auto; border: 1px solid #ddd; padding: 20px;">
                        <h2 style="color: #4CAF50;">Success! Payment Received</h2>
                        <p>Hello <strong>%s</strong>,</p>
                        <p>Your payment for invoice <strong>#%s</strong> has been successfully processed.</p>
                        <hr>
                        <p><strong>Amount:</strong> ₹%s</p>
                        <p><strong>Merchant:</strong> %s</p>
                        <p><strong>Reference ID:</strong> %s</p>
                        <hr>
                        <p>Thank you for using BillMe!</p>
                    </div>
                </body>
                </html>
                """, 
                invoice.getResolvedCustomerName(),
                invoice.getInvoiceNumber(),
                invoice.getTotalPayable(),
                invoice.getMerchant().getBusinessName(),
                invoice.getTransaction() != null ? invoice.getTransaction().getExternalReference() : "N/A"
            );

            helper.setText(content, true);
            mailSender.send(message);
            log.info("✅ [EMAIL SENT] Customer payment success email for Invoice {}", invoice.getInvoiceNumber());
        } catch (Exception e) {
            log.error("🚨 [EMAIL ERROR] Failed to send customer email for Invoice {}: {}", invoice.getInvoiceNumber(), e.getMessage());
        }
    }

    @Override
    @Async
    public void sendMerchantPaymentReceivedEmail(Invoice invoice) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setTo(invoice.getMerchant().getUser().getEmail());
            helper.setSubject("New Payment Received - BillMe Invoice #" + invoice.getInvoiceNumber());

            String content = String.format("""
                <html>
                <body style="font-family: Arial, sans-serif; color: #333;">
                    <div style="max-width: 600px; margin: auto; border: 1px solid #ddd; padding: 20px;">
                        <h2 style="color: #2196F3;">You've Received a Payment!</h2>
                        <p>Hello <strong>%s</strong>,</p>
                        <p>A payment has been successfully received for invoice <strong>#%s</strong>.</p>
                        <hr>
                        <p><strong>Amount Credited to Escrow:</strong> ₹%s</p>
                        <p><strong>Customer:</strong> %s</p>
                        <p><strong>Reference ID:</strong> %s</p>
                        <hr>
                        <p>Stay billing, keep earning with BillMe.</p>
                    </div>
                </body>
                </html>
                """, 
                invoice.getMerchant().getBusinessName(),
                invoice.getInvoiceNumber(),
                invoice.getTotalPayable().subtract(invoice.getProcessingFee()),
                invoice.getResolvedCustomerName(),
                invoice.getTransaction() != null ? invoice.getTransaction().getExternalReference() : "N/A"
            );

            helper.setText(content, true);
            mailSender.send(message);
            log.info("✅ [EMAIL SENT] Merchant payment received email for Invoice {}", invoice.getInvoiceNumber());
        } catch (Exception e) {
            log.error("🚨 [EMAIL ERROR] Failed to send merchant email for Invoice {}: {}", invoice.getInvoiceNumber(), e.getMessage());
        }
    }
}