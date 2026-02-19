package com.billme.payment;

import com.billme.invoice.Invoice;
import com.billme.invoice.InvoiceStatus;
import com.billme.invoice.PaymentMethod;
import com.billme.repository.InvoiceRepository;
import com.billme.repository.TransactionRepository;
import com.billme.repository.UserRepository;
import com.billme.repository.WalletRepository;
import com.billme.transaction.Transaction;
import com.billme.transaction.TransactionStatus;
import com.billme.transaction.TransactionType;
import com.billme.user.User;
import com.billme.wallet.Wallet;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FacePayService {

    private final InvoiceRepository invoiceRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    @Transactional
    public String payInvoice(Long invoiceId) {

        User customer = getLoggedInUser();

        Invoice invoice = invoiceRepository
                .findByIdAndCustomer_User_Id(invoiceId, customer.getId())
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new RuntimeException("Invoice already paid");
        }

        simulateFaceVerification(customer);

        Wallet customerWallet = walletRepository
                .findByUser_Id(customer.getId())
                .orElseThrow(() -> new RuntimeException("Customer wallet not found"));

        Wallet merchantWallet = walletRepository
                .findByUser_Id(invoice.getMerchant().getUser().getId())
                .orElseThrow(() -> new RuntimeException("Merchant wallet not found"));

        BigDecimal amount = invoice.getAmount();

        if (customerWallet.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient wallet balance");
        }

        // 💰 Deduct & Credit
        customerWallet.setBalance(customerWallet.getBalance().subtract(amount));
        merchantWallet.setBalance(merchantWallet.getBalance().add(amount));

        walletRepository.save(customerWallet);
        walletRepository.save(merchantWallet);

        // 🧾 Create transaction (Wallet-based model)
        Transaction transaction = Transaction.builder()
                .senderWallet(customerWallet)
                .receiverWallet(merchantWallet)
                .amount(amount)
                .transactionType(TransactionType.FACE_PAY)
                .status(TransactionStatus.SUCCESS)
                .externalReference(null)
                .build();

        transactionRepository.save(transaction);

        // 🧾 Update invoice
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaymentMethod(PaymentMethod.FACE_PAY);
        invoice.setPaidAt(LocalDateTime.now());
        invoice.setTransaction(transaction);

        invoiceRepository.save(invoice);

        return "FacePay successful";
    }


    private User getLoggedInUser() {

        String email = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // 🎭 Simulated realistic face verification
    private void simulateFaceVerification(User customer) {

        // In real system:
        // - capture embedding from camera
        // - compare with stored embedding
        // - compute similarity score

        // For now:
        if (!customer.isActive()) {
            throw new RuntimeException("Face verification failed");
        }
    }
}
