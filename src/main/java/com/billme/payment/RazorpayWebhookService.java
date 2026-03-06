package com.billme.payment;

import com.billme.invoice.Invoice;
import com.billme.invoice.InvoiceStatus;
import com.billme.invoice.PaymentMethod;
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
            return;
        }

        JSONObject paymentEntity = json
                .getJSONObject("payload")
                .getJSONObject("payment")
                .getJSONObject("entity");

        String orderId = paymentEntity.getString("order_id");
        String paymentId = paymentEntity.getString("id");
        int webhookAmount = paymentEntity.getInt("amount");

        JSONObject paymentDetails =
                verificationService.fetchPaymentDetails(paymentId);

        String apiStatus = paymentDetails.getString("status");
        int apiAmount = paymentDetails.getInt("amount");
        String apiOrderId = paymentDetails.getString("order_id");

        if (!"captured".equals(apiStatus)) {
            throw new RuntimeException("Payment not captured");
        }

        if (!orderId.equals(apiOrderId)) {
            throw new RuntimeException("Order ID mismatch");
        }

        if (webhookAmount != apiAmount) {
            throw new RuntimeException("Amount mismatch");
        }

        Invoice invoice = invoiceRepository
                .findByRazorpayOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        // 🔒 Idempotency check
        if (paymentTransactionRepository
                .findByRazorpayPaymentId(paymentId)
                .isPresent()) {
            return;
        }

        PaymentTransaction transaction = PaymentTransaction.builder()
                .invoice(invoice)
                .razorpayPaymentId(paymentId)
                .razorpayOrderId(orderId)
                .amount(BigDecimal.valueOf(apiAmount)
                        .divide(BigDecimal.valueOf(100)))
                .currency(paymentDetails.getString("currency"))
                .status(apiStatus)
                .capturedAt(LocalDateTime.now())
                .rawPayload(payload)
                .build();

        paymentTransactionRepository.save(transaction);

        // 🔥 Mark invoice PAID
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(LocalDateTime.now());

        // 🔓 Release payment lock
        invoice.setPaymentInProgress(false);

        // 🔥 Set payment method
        invoice.setPaymentMethod(PaymentMethod.UPI_PAY);

        invoiceRepository.save(invoice);

        settlementService.settlePayment(
                invoice,
                invoice.getSubtotal(),
                paymentId
        );
    }
}