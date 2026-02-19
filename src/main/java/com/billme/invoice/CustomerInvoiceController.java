package com.billme.invoice;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/customer/invoices")
@RequiredArgsConstructor
public class CustomerInvoiceController {

    private final InvoiceService invoiceService;

    @GetMapping
    public List<CustomerInvoiceResponse> getAllInvoices() {
        return invoiceService.getCustomerInvoices();
    }

    @GetMapping("/pending")
    public List<CustomerInvoiceResponse> getPendingInvoices() {
        return invoiceService.getPendingInvoices();
    }

    @GetMapping("/{id}")
    public CustomerInvoiceResponse getInvoiceById(@PathVariable Long id) {
        return invoiceService.getInvoiceById(id);
    }
}
