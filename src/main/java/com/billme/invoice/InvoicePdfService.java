package com.billme.invoice;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class InvoicePdfService {

    @Value("${app.payment.url}")
    private String paymentUrl;

    public byte[] generateInvoicePdf(Invoice invoice) {

        StringBuilder rows = new StringBuilder();

        invoice.getItems().forEach(item -> {
            rows.append("<tr>")
                    .append("<td>").append(item.getProductNameSnapshot()).append("</td>")
                    .append("<td>").append(item.getQuantity()).append("</td>")
                    .append("<td>").append(item.getUnitPrice()).append("</td>")
                    .append("<td>").append(item.getGstRate()).append("%</td>")
                    .append("<td>").append(item.getTotalPrice()).append("</td>")
                    .append("</tr>");
        });

        String payLink = paymentUrl + "/" + invoice.getId();

        String html = """
                <html>
                <head>
                <style>
                    body { font-family: Arial; padding: 40px; }
                    h1 { color: #2c3e50; }
                    table { width: 100%; border-collapse: collapse; margin-top: 20px; }
                    th, td { border: 1px solid #ccc; padding: 10px; text-align: center; }
                    th { background: #f5f5f5; }
                    .total { margin-top:20px; font-size:16px; }
                    .pay-btn {
                        display:inline-block;
                        margin-top:30px;
                        padding:12px 20px;
                        background:#2ecc71;
                        color:white;
                        text-decoration:none;
                        border-radius:6px;
                    }
                </style>
                </head>

                <body>

                <h1>BillMe Invoice</h1>

                <p><b>Invoice Number:</b> %s</p>
                <p><b>Merchant:</b> %s</p>
                <p><b>Customer:</b> %s</p>
                <p><b>Date:</b> %s</p>

                <table>
                <tr>
                    <th>Product</th>
                    <th>Qty</th>
                    <th>Price</th>
                    <th>GST</th>
                    <th>Total</th>
                </tr>

                %s

                </table>

                <div class="total">
                <p>Subtotal: %s</p>
                <p>Processing Fee: %s</p>
                <h2>Total Payable: %s</h2>
                </div>

                <a class="pay-btn" href="%s">Pay Now</a>

                </body>
                </html>
                """.formatted(
                invoice.getInvoiceNumber(),
                invoice.getMerchant().getBusinessName(),
                invoice.getCustomer().getName(),
                invoice.getIssuedAt(),
                rows,
                invoice.getSubtotal(),
                invoice.getProcessingFee(),
                invoice.getTotalPayable(),
                payLink
        );

        try {

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, null);
            builder.toStream(outputStream);
            builder.run();

            return outputStream.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate invoice PDF", e);
        }
    }
}