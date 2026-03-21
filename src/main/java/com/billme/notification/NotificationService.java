package com.billme.notification;

import com.billme.user.User;
import com.billme.invoice.Invoice;
import com.billme.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final EmailService emailService;

    // Internal helper (participates in its own transaction)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createNotification(User user, String message, NotificationType type) {
        Notification notification = Notification.builder()
                .user(user)
                .message(message)
                .type(type)
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();
        notificationRepository.save(notification);
    }

    @Transactional
    public void sendPaymentNotifications(Invoice invoice) {
        log.info("🔔 Processing notifications for Invoice {}", invoice.getInvoiceNumber());

        // 🚀 Initialize LAZY proxies
        try {
            if (invoice.getMerchant() != null) {
                invoice.getMerchant().getBusinessName();
                if (invoice.getMerchant().getUser() != null) {
                    invoice.getMerchant().getUser().getEmail();
                }
            }
            if (invoice.getCustomer() != null) {
                invoice.getCustomer().getName();
                if (invoice.getCustomer().getUser() != null) {
                    invoice.getCustomer().getUser().getEmail();
                }
            }
        } catch (Exception e) {
            log.warn("Proxy initialization failed: {}", e.getMessage());
        }

        // 1. In-App Notifications (Best Effort - Don't fail the payment)
        try {
            if (invoice.getCustomer() != null && invoice.getCustomer().getUser() != null) {
                String customerMsg = String.format("Payment of ₹%s for Invoice %s is successful.",
                        invoice.getTotalPayable(), invoice.getInvoiceNumber());
                createNotification(invoice.getCustomer().getUser(), customerMsg, NotificationType.PAYMENT_SUCCESS);
            }

            if (invoice.getMerchant() != null && invoice.getMerchant().getUser() != null) {
                String merchantMsg = String.format("Received ₹%s from %s for Invoice %s.",
                        invoice.getTotalPayable().subtract(invoice.getProcessingFee()),
                        invoice.getResolvedCustomerName(),
                        invoice.getInvoiceNumber());
                createNotification(invoice.getMerchant().getUser(), merchantMsg, NotificationType.PAYMENT_RECEIVED);
            }
        } catch (Exception e) {
            log.error("Failed to create in-app notifications: {}", e.getMessage());
            // 🚨 We do NOT re-throw, so the outer transaction (payment) can commit.
        }

        // 3. Email Synchronization (Safe)

        // 3. Trigger Emails AFTER Transaction Commit (Safe Guard)
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        log.info("Transaction committed. Triggering emails for Invoice {}", invoice.getInvoiceNumber());
                        emailService.sendCustomerPaymentSuccessEmail(invoice);
                        emailService.sendMerchantPaymentReceivedEmail(invoice);
                    }
                }
        );
    }

    @Transactional(readOnly = true)
    public List<Notification> getUserNotifications(User user) {
        return notificationRepository.findByUserOrderByCreatedAtDesc(user);
    }
    
    @Transactional
    public void markAsRead(Long notificationId, User user) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            if (n.getUser().getId().equals(user.getId())) {
                n.setRead(true);
                notificationRepository.save(n);
            }
        });
    }
}
