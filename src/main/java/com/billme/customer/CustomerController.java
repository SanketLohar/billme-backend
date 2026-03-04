package com.billme.customer;

import com.billme.customer.dto.CustomerLookupResponse;
import com.billme.repository.CustomerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerProfileRepository customerProfileRepository;

    // ==========================================
    // EXISTING PROFILE API
    // ==========================================
    @GetMapping("/profile")
    public String getCustomerProfile(Authentication authentication) {
        return "Customer profile accessed by: " + authentication.getName();
    }

    // ==========================================
    // NEW API → LOOKUP CUSTOMER BY EMAIL
    // ==========================================
    @GetMapping("/email/{email}")
    public CustomerLookupResponse findCustomerByEmail(
            @PathVariable String email) {

        CustomerProfile profile = customerProfileRepository
                .findByUser_Email(email)
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        return CustomerLookupResponse.builder()
                .id(profile.getId())
                .name(profile.getName())
                .email(profile.getUser().getEmail())
                .build();
    }
}