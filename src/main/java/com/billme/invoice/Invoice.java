package com.billme.invoice;

import com.billme.customer.CustomerProfile;
import com.billme.merchant.MerchantProfile;
import com.billme.transaction.Transaction;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;



@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "invoices")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String invoiceNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    private MerchantProfile merchant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private CustomerProfile customer;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    @OneToMany(mappedBy = "invoice",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<InvoiceItem> items = new ArrayList<>();

    @OneToOne
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    private LocalDateTime issuedAt;
    private LocalDateTime paidAt;

    @Column
    private String razorpayOrderId;


    @PrePersist
    public void prePersist() {
        this.issuedAt = LocalDateTime.now();
        this.status = InvoiceStatus.UNPAID;
        this.invoiceNumber = "INV-" + System.currentTimeMillis();
    }

    @Column(nullable = false)
    private BigDecimal subtotal;

    @Column(nullable = false)
    private BigDecimal processingFee;

    @Column(nullable = false)
    private BigDecimal totalPayable;

    @Column(name = "payment_token", unique = true, nullable = false)
    private String paymentToken;

    @Column(name = "refund_window_expiry")
    private LocalDateTime refundWindowExpiry;


    public String getPaymentToken() {
        return paymentToken;
    }

    public void setPaymentToken(String paymentToken) {
        this.paymentToken = paymentToken;
    }

}