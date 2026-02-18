package com.billme.invoice;

import com.billme.merchant.MerchantProfile;
import com.billme.repository.MerchantProfileRepository;
import com.billme.repository.UserRepository;
import com.billme.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/merchant")
@RequiredArgsConstructor
public class MerchantInvoiceController {

    private final InvoiceService invoiceService;
    private final UserRepository userRepository;
    private final MerchantProfileRepository merchantProfileRepository;

    @PostMapping("/invoices")
    public ResponseEntity<String> createInvoice(
            @RequestBody CreateInvoiceRequest request) {

        // 1️⃣ Get logged-in merchant email
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        String email = authentication.getName();

        // 2️⃣ Fetch user
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 3️⃣ Fetch merchant profile
        MerchantProfile merchant = merchantProfileRepository
                .findByUser_Id(user.getId())
                .orElseThrow(() -> new RuntimeException("Merchant profile not found"));

        // 4️⃣ Create invoice
        invoiceService.createInvoice(
                merchant.getId(),
                request.getCustomerId(),
                request.getAmount()
        );

        return ResponseEntity.ok("Invoice created successfully");
    }
}
