package com.billme.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundEmailService {

    private final JavaMailSender mailSender;

    public void sendRefundRequestEmail(
            String merchantEmail,
            String invoiceNumber,
            String customerName,
            BigDecimal amount,
            String paymentMethod,
            String approveToken,
            String rejectToken) {

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(merchantEmail);
            helper.setSubject("Refund Request: " + invoiceNumber);

            String approveUrl = "http://localhost:8080/api/refund/email/approve/" + approveToken;
            String rejectUrl = "http://localhost:8080/api/refund/email/reject/" + rejectToken;

            String htmlContent = String.format("""
                <html>
                <body style="font-family: Arial, sans-serif; color: #333;">
                    <h2>Refund Request Received</h2>
                    <p>A customer has requested a refund for an invoice.</p>
                    <ul>
                        <li><strong>Invoice Number:</strong> %s</li>
                        <li><strong>Customer:</strong> %s</li>
                        <li><strong>Amount:</strong> ₹%.2f</li>
                        <li><strong>Payment Method:</strong> %s</li>
                    </ul>
                    <p>Please approve or reject this request:</p>
                    <div style="margin-top: 20px;">
                        <a href="%s" style="background-color: #28a745; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px; margin-right: 10px;">Approve Refund</a>
                        <a href="%s" style="background-color: #dc3545; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px;">Reject Refund</a>
                    </div>
                </body>
                </html>
            """, invoiceNumber, customerName, amount, paymentMethod, approveUrl, rejectUrl);

            helper.setText(htmlContent, true);
            mailSender.send(message);

        } catch (MessagingException e) {
            log.error("Failed to send refund request email to {}: {}", merchantEmail, e.getMessage());
        }
    }

    public void sendRefundCompletedEmail(String customerEmail, String invoiceNumber) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(customerEmail);
            helper.setSubject("Refund Completed");

            String htmlContent = String.format("""
                <html>
                <body style="font-family: Arial, sans-serif; color: #333;">
                    <h2>Refund Processed</h2>
                    <p>Your refund for Invoice <strong>%s</strong> has been successfully processed.</p>
                    <p>The funds will be credited to your original payment method shortly.</p>
                </body>
                </html>
            """, invoiceNumber);

            helper.setText(htmlContent, true);
            mailSender.send(message);

        } catch (MessagingException e) {
            log.error("Failed to send refund completed email to {}: {}", customerEmail, e.getMessage());
        }
    }
}
