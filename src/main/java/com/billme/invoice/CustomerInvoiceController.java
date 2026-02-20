package com.billme.invoice;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import java.util.List;

@RestController
@RequestMapping("/customer/invoices")
@RequiredArgsConstructor
public class CustomerInvoiceController {

    private final InvoiceService invoiceService;

    @GetMapping
    public List<CustomerInvoiceResponse> getAllInvoices(Authentication authentication) {
        return invoiceService.getCustomerInvoices(authentication.getName());
    }

    @GetMapping("/pending")
    public List<CustomerInvoiceResponse> getPending(Authentication authentication) {
        return invoiceService.getPendingInvoices(authentication.getName());
    }

    @GetMapping("/{id}")
    public CustomerInvoiceResponse getInvoice(
            @PathVariable Long id,
            Authentication authentication) {
        return invoiceService.getInvoiceById(id, authentication.getName());
    }
}
