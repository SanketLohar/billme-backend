package com.billme.invoice;

import com.billme.invoice.dto.InvoiceItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InvoiceTemplateService {

    private final TemplateEngine templateEngine;

    @Value("${app.frontend.url}")
    private String frontendUrl;

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
                invoice.getResolvedCustomerName()
        );

        context.setVariable(
                "customerEmail",
                invoice.getResolvedCustomerEmail()
        );

        context.setVariable(
                "customerAddress",
                invoice.getCustomer() != null && invoice.getCustomer().getAddress() != null
                        ? invoice.getCustomer().getAddress()
                        : ""
        );

        context.setVariable("subtotal", invoice.getSubtotal());
        context.setVariable("processingFee", invoice.getProcessingFee());
        context.setVariable("totalPayable", invoice.getTotalPayable());

        java.math.BigDecimal gstTotal = invoice.getItems().stream()
                .map(com.billme.invoice.InvoiceItem::getGstAmount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        context.setVariable("gstTotal", gstTotal);

        java.util.Map<java.math.BigDecimal, java.util.Map<String, Object>> gstSlabs = new java.util.HashMap<>();
        
        boolean isIntraState = invoice.getIgstTotal() == null || invoice.getIgstTotal().compareTo(java.math.BigDecimal.ZERO) == 0;
        // If legacy invoice with only gstTotal, assume intra-state (default)
        if (invoice.getIgstTotal() == null && invoice.getCgstTotal() == null && invoice.getGstTotal() != null) {
            isIntraState = true;
        }

        for (com.billme.invoice.InvoiceItem item : invoice.getItems()) {
            if (item.getGstRate().compareTo(java.math.BigDecimal.ZERO) > 0) {
                java.math.BigDecimal rate = item.getGstRate().stripTrailingZeros();
                java.util.Map<String, Object> slab = gstSlabs.getOrDefault(rate, new java.util.HashMap<>());
                
                java.math.BigDecimal currentGst = (java.math.BigDecimal) slab.getOrDefault("gstAmount", java.math.BigDecimal.ZERO);
                java.math.BigDecimal currentCgst = (java.math.BigDecimal) slab.getOrDefault("cgstAmount", java.math.BigDecimal.ZERO);
                java.math.BigDecimal currentSgst = (java.math.BigDecimal) slab.getOrDefault("sgstAmount", java.math.BigDecimal.ZERO);
                java.math.BigDecimal currentIgst = (java.math.BigDecimal) slab.getOrDefault("igstAmount", java.math.BigDecimal.ZERO);

                slab.put("rate", rate.toPlainString());
                slab.put("gstAmount", currentGst.add(item.getGstAmount()));
                
                if (isIntraState) {
                    java.math.BigDecimal halfRate = rate.divide(java.math.BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP).stripTrailingZeros();
                    slab.put("halfRate", halfRate.toPlainString());
                    
                    // Use item fields if available, otherwise fallback to 50/50 split
                    java.math.BigDecimal itemCgst = item.getCgstAmount();
                    java.math.BigDecimal itemSgst = item.getSgstAmount();
                    if (itemCgst == null) {
                        itemCgst = item.getGstAmount().divide(java.math.BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP);
                        itemSgst = item.getGstAmount().subtract(itemCgst);
                    }
                    slab.put("cgstAmount", currentCgst.add(itemCgst));
                    slab.put("sgstAmount", currentSgst.add(itemSgst));
                } else {
                    slab.put("igstAmount", currentIgst.add(item.getIgstAmount() != null ? item.getIgstAmount() : item.getGstAmount()));
                }
                
                gstSlabs.put(rate, slab);
            }
        }

        java.util.List<java.util.Map<String, Object>> gstSummary = new java.util.ArrayList<>(gstSlabs.values());
        gstSummary.sort((m1, m2) -> new java.math.BigDecimal(m1.get("rate").toString())
                .compareTo(new java.math.BigDecimal(m2.get("rate").toString())));

        context.setVariable("gstSummary", gstSummary);
        context.setVariable("isIntraState", isIntraState);
        
        context.setVariable("gstRegistered", invoice.getMerchant().isGstRegistered());

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
                frontendUrl + "/pay-invoice.html?invoice=" + invoice.getInvoiceNumber() + "&token=" + invoice.getPaymentToken()
        );

        return templateEngine.process("invoice-template", context);
    }
}