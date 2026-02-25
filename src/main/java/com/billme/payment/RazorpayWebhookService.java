package com.billme.payment;

import com.billme.invoice.Invoice;
import com.billme.repository.InvoiceRepository;
import com.billme.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RazorpayWebhookService {

    private final InvoiceRepository invoiceRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final RazorpayVerificationService verificationService;
    private final PaymentSettlementService settlementService;

    @Transactional
    public void processWebhook(String payload) {

        JSONObject json = new JSONObject(payload);

        String event = json.optString("event", "");
        if (!"payment.captured".equals(event)) {
            System.out.println("Ignoring event: " + event);
            return;
        }

        JSONObject paymentEntity = json
                .getJSONObject("payload")
                .getJSONObject("payment")
                .getJSONObject("entity");

        String orderId = paymentEntity.getString("order_id");
        String paymentId = paymentEntity.getString("id");
        int webhookAmount = paymentEntity.getInt("amount");

        // 🔐 Verify with Razorpay API
        JSONObject paymentDetails =
                verificationService.fetchPaymentDetails(paymentId);

        String apiStatus = paymentDetails.getString("status");
        int apiAmount = paymentDetails.getInt("amount");
        String apiOrderId = paymentDetails.getString("order_id");
        String paymentMethod = paymentDetails.optString("method");

        if (!"captured".equals(apiStatus)) {
            throw new RuntimeException("Payment not captured according to Razorpay API");
        }

        if (!orderId.equals(apiOrderId)) {
            throw new RuntimeException("Order ID mismatch");
        }

        if (webhookAmount != apiAmount) {
            throw new RuntimeException("Amount mismatch");
        }

        // 🧾 Fetch Invoice
        Invoice invoice = invoiceRepository
                .findByRazorpayOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        // 🔁 Prevent duplicate webhook
        if (paymentTransactionRepository
                .findByRazorpayPaymentId(paymentId)
                .isPresent()) {

            System.out.println("Duplicate webhook ignored for paymentId: " + paymentId);
            return;
        }

        // 💾 Save PaymentTransaction
        PaymentTransaction transaction = PaymentTransaction.builder()
                .invoice(invoice)
                .razorpayPaymentId(paymentId)
                .razorpayOrderId(orderId)
                .amount(BigDecimal.valueOf(apiAmount)
                        .divide(BigDecimal.valueOf(100)))
                .currency(paymentDetails.getString("currency"))
                .status(apiStatus)
                .paymentMethod(paymentMethod)
                .capturedAt(LocalDateTime.now())
                .rawPayload(payload)
                .build();

        paymentTransactionRepository.save(transaction);

        // 💰 Call Settlement Layer
        settlementService.settlePayment(
                invoice,
                invoice.getSubtotal(),
                paymentId
        );

        System.out.println("✅ Payment verified, transaction stored, settlement completed");
    }
}