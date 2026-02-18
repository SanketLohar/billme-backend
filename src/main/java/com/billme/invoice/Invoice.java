package com.billme.invoice;

import com.billme.customer.CustomerProfile;
import com.billme.merchant.MerchantProfile;
import com.billme.transaction.Transaction;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    private String invoiceNumber;

    @ManyToOne
    @JoinColumn(name = "merchant_id", nullable = false)
    private MerchantProfile merchant;

    @ManyToOne
    @JoinColumn(name = "customer_id", nullable = false)
    private CustomerProfile customer;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private InvoiceStatus status;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    @OneToOne
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    private LocalDateTime issuedAt;

    private LocalDateTime paidAt;

    @PrePersist
    public void prePersist() {
        this.issuedAt = LocalDateTime.now();
        this.status = InvoiceStatus.UNPAID;
        this.invoiceNumber = "INV-" + System.currentTimeMillis();
    }
}
