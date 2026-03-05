package com.billme.invoice;

import com.billme.invoice.dto.InvoiceItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InvoiceTemplateService {

    private final TemplateEngine templateEngine;

    public String generateInvoiceHtml(Invoice invoice) {

        Context context = new Context();

        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

        context.setVariable("invoiceNumber", invoice.getInvoiceNumber());

        context.setVariable(
                "invoiceDate",
                invoice.getIssuedAt() != null
                        ? invoice.getIssuedAt().format(formatter)
                        : ""
        );

        context.setVariable(
                "status",
                invoice.getStatus() != null
                        ? invoice.getStatus().name()
                        : ""
        );

        context.setVariable(
                "merchantName",
                invoice.getMerchant().getBusinessName()
        );

        context.setVariable(
                "merchantAddress",
                invoice.getMerchant().getAddress() != null
                        ? invoice.getMerchant().getAddress()
                        : ""
        );

        context.setVariable(
                "merchantGstin",
                invoice.getMerchant().getGstin()
        );

        context.setVariable(
                "customerName",
                invoice.getCustomer().getName()
        );

        context.setVariable(
                "customerEmail",
                invoice.getCustomer().getUser().getEmail()
        );

        context.setVariable(
                "customerAddress",
                invoice.getCustomer().getAddress() != null
                        ? invoice.getCustomer().getAddress()
                        : ""
        );

        context.setVariable("subtotal", invoice.getSubtotal());
        context.setVariable("processingFee", invoice.getProcessingFee());
        context.setVariable("totalPayable", invoice.getTotalPayable());

        context.setVariable(
                "items",
                invoice.getItems().stream()
                        .map(item ->
                                InvoiceItemResponse.builder()
                                        .productName(item.getProductNameSnapshot())
                                        .quantity(item.getQuantity())
                                        .unitPrice(item.getUnitPrice())
                                        .gstRate(item.getGstRate())
                                        .gstAmount(item.getGstAmount())
                                        .totalPrice(item.getTotalPrice())
                                        .build()
                        )
                        .collect(Collectors.toList())
        );

        context.setVariable(
                "payUrl",
                "http://localhost:5173/pay/" + invoice.getId()
        );

        return templateEngine.process("invoice-template", context);
    }
}