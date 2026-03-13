package com.billme.email;

import com.billme.invoice.Invoice;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Service
@RequiredArgsConstructor
public class PaymentSuccessEmailService {

    private final JavaMailSender mailSender;

    public void sendPaymentSuccessEmail(Invoice invoice) {

        try {

            MimeMessage message = mailSender.createMimeMessage();

            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setTo(invoice.getResolvedCustomerEmail());

            helper.setSubject("Payment Received - Invoice " + invoice.getInvoiceNumber());

            helper.setText("""
                    Hello %s,

                    Your payment has been successfully received.

                    Invoice Number: %s
                    Amount Paid: ₹%s
                    Merchant: %s

                    Thank you for using BillMe.

                    Regards,
                    BillMe Team
                    """.formatted(
                    invoice.getResolvedCustomerName(),
                    invoice.getInvoiceNumber(),
                    invoice.getTotalPayable(),
                    invoice.getMerchant().getBusinessName()
            ));

            mailSender.send(message);

        } catch (Exception e) {
            throw new RuntimeException("Failed to send payment success email", e);
        }
    }
}