package com.billme.payment;

import com.billme.customer.CustomerProfile;
import com.billme.invoice.Invoice;
import com.billme.invoice.InvoiceStatus;
import com.billme.invoice.PaymentMethod;
import com.billme.repository.CustomerProfileRepository;
import com.billme.repository.InvoiceRepository;
import com.billme.repository.TransactionRepository;
import com.billme.repository.UserRepository;
import com.billme.repository.WalletRepository;
import com.billme.security.face.FaceRecognitionUtil;
import com.billme.transaction.Transaction;
import com.billme.transaction.TransactionStatus;
import com.billme.transaction.TransactionType;
import com.billme.user.User;
import com.billme.wallet.Wallet;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FacePayService {

    private final InvoiceRepository invoiceRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final CustomerProfileRepository customerProfileRepository;

    private static final double FACE_MATCH_THRESHOLD = 0.80;

    @Transactional
    public String payInvoice(Long invoiceId, String paymentEmbedding) {

        User customer = getLoggedInUser();

        Invoice invoice = invoiceRepository
                .findByIdAndCustomer_User_Id(invoiceId, customer.getId())
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Invoice already paid"
            );
        }


        // 🔐 REAL FACE MATCH
        CustomerProfile profile = customerProfileRepository
                .findByUser_Id(customer.getId())
                .orElseThrow(() -> new RuntimeException("Customer profile not found"));

        boolean match = FaceRecognitionUtil.isMatch(
                profile.getFaceEmbeddings(),
                paymentEmbedding,
                FACE_MATCH_THRESHOLD
        );

        if (!match) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Face verification failed"
            );
        }


        Wallet customerWallet = walletRepository
                .findByUser(customer)
                .orElseThrow(() -> new RuntimeException("Customer wallet not found"));

        Wallet merchantWallet = walletRepository
                .findByUser(invoice.getMerchant().getUser())
                .orElseThrow(() -> new RuntimeException("Merchant wallet not found"));

        BigDecimal amount = invoice.getAmount();

        if (customerWallet.getBalance().compareTo(amount) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Insufficient wallet balance"
            );
        }


        // 💰 Atomic Transfer
        customerWallet.setBalance(customerWallet.getBalance().subtract(amount));
        merchantWallet.setBalance(merchantWallet.getBalance().add(amount));

        walletRepository.save(customerWallet);
        walletRepository.save(merchantWallet);

        // 🧾 Create transaction
        Transaction transaction = Transaction.builder()
                .senderWallet(customerWallet)
                .receiverWallet(merchantWallet)
                .amount(amount)
                .transactionType(TransactionType.FACE_PAY)
                .status(TransactionStatus.SUCCESS)
                .createdAt(LocalDateTime.now())
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
}
