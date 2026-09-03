package com.enterprise.subscription;

import com.enterprise.invoice.Invoice;
import com.enterprise.invoice.InvoiceService;
import com.enterprise.notification.NotificationService;
import com.enterprise.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final CustomerSubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionRequestRepository requestRepository;
    private final PaymentRetryRepository paymentRetryRepository;
    private final InvoiceService invoiceService;
    private final NotificationService notificationService;

    public SubscriptionService(CustomerSubscriptionRepository subscriptionRepository,
                               SubscriptionPlanRepository planRepository,
                               SubscriptionRequestRepository requestRepository,
                               PaymentRetryRepository paymentRetryRepository,
                               InvoiceService invoiceService,
                               NotificationService notificationService) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.requestRepository = requestRepository;
        this.paymentRetryRepository = paymentRetryRepository;
        this.invoiceService = invoiceService;
        this.notificationService = notificationService;
    }

    // ============================================================
    // SUBSCRIPTION REQUEST MANAGEMENT
    // ============================================================

    @Transactional
    public SubscriptionRequest createRequest(Long customerId, String customerEmail, Long merchantId, Long planId) {
        Long tenantId = TenantContext.getRequiredTenantId();

        SubscriptionPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("Plan not found"));

        // Check if there's already a pending request for this customer and plan
        List<SubscriptionRequest> pendingRequests = requestRepository.findByCustomerId(customerId)
                .stream()
                .filter(r -> r.getPlanId().equals(planId) && r.getStatus() == SubscriptionRequest.RequestStatus.PENDING)
                .toList();

        if (!pendingRequests.isEmpty()) {
            throw new RuntimeException("You already have a pending request for this plan");
        }

        SubscriptionRequest request = new SubscriptionRequest();
        request.setCustomerId(customerId);
        request.setCustomerEmail(customerEmail);
        request.setPlanId(planId);
        request.setPlanName(plan.getName());
        request.setMerchantId(merchantId);
        request.setTenantId(tenantId);
        request.setStatus(SubscriptionRequest.RequestStatus.PENDING);

        SubscriptionRequest saved = requestRepository.save(request);

        // Send notification to merchant admin
        notificationService.createNotification(
                merchantId,
                "SUBSCRIPTION_REQUEST",
                "New Subscription Request",
                "Customer " + customerEmail + " wants to subscribe to " + plan.getName(),
                "/merchant/subscriptions/requests"
        );

        log.info("✅ Subscription request created for customer {} to plan {}", customerEmail, plan.getName());
        return saved;
    }

    @Transactional
    public CustomerSubscription approveRequest(Long requestId, Long adminId) {
        SubscriptionRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        if (request.getStatus() != SubscriptionRequest.RequestStatus.PENDING) {
            throw new RuntimeException("Request already processed");
        }

        // Create the actual subscription
        CustomerSubscription subscription = createSubscriptionInternal(
                request.getCustomerId(),
                request.getCustomerEmail(),
                request.getMerchantId(),
                request.getPlanId()
        );

        // Update request status
        request.setStatus(SubscriptionRequest.RequestStatus.APPROVED);
        request.setAssignedBy(adminId);
        request.setAssignedAt(LocalDateTime.now());
        request.setUpdatedAt(LocalDateTime.now());
        requestRepository.save(request);

        // Notify customer
        notificationService.createNotification(
                request.getCustomerId(),
                "SUBSCRIPTION_APPROVED",
                "Subscription Approved",
                "Your subscription to " + request.getPlanName() + " has been approved!",
                "/subscriptions/my"
        );

        log.info("✅ Subscription request {} approved by admin {}", requestId, adminId);
        return subscription;
    }

    @Transactional
    public void rejectRequest(Long requestId, Long adminId, String reason) {
        SubscriptionRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        if (request.getStatus() != SubscriptionRequest.RequestStatus.PENDING) {
            throw new RuntimeException("Request already processed");
        }

        request.setStatus(SubscriptionRequest.RequestStatus.REJECTED);
        request.setAssignedBy(adminId);
        request.setAssignedAt(LocalDateTime.now());
        request.setUpdatedAt(LocalDateTime.now());
        request.setNotes(reason);
        requestRepository.save(request);

        // Notify customer
        notificationService.createNotification(
                request.getCustomerId(),
                "SUBSCRIPTION_REJECTED",
                "Subscription Rejected",
                "Your subscription request was rejected. Reason: " + (reason != null ? reason : "Not specified"),
                "/subscriptions/plans"
        );

        log.info("✅ Subscription request {} rejected by admin {}", requestId, adminId);
    }

    public List<SubscriptionRequest> getPendingRequests(Long merchantId) {
        Long tenantId = TenantContext.getRequiredTenantId();
        return requestRepository.findPendingByMerchantId(tenantId, merchantId);
    }

    public List<SubscriptionRequest> getRequestsForCustomer(Long customerId) {
        return requestRepository.findByCustomerId(customerId);
    }

    public List<SubscriptionRequest> getAllRequestsForMerchant(Long merchantId) {
        return requestRepository.findByMerchantIdAndStatus(merchantId, SubscriptionRequest.RequestStatus.PENDING);
    }

    public long getPendingRequestCount(Long merchantId) {
        return requestRepository.countByMerchantIdAndStatus(merchantId, SubscriptionRequest.RequestStatus.PENDING);
    }

    // ============================================================
    // PLAN MANAGEMENT
    // ============================================================

    public List<SubscriptionPlan> getPlansForTenant(Long tenantId) {
        return planRepository.findActiveByTenantId(tenantId);
    }

    public SubscriptionPlan getPlanById(Long id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Plan not found: " + id));
    }

    @Transactional
    public SubscriptionPlan createPlan(SubscriptionPlan plan) {
        Long tenantId = TenantContext.getRequiredTenantId();
        plan.setTenantId(tenantId);
        return planRepository.save(plan);
    }

    @Transactional
    public SubscriptionPlan updatePlan(Long id, SubscriptionPlan updatedPlan) {
        SubscriptionPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Plan not found"));
        plan.setName(updatedPlan.getName());
        plan.setDescription(updatedPlan.getDescription());
        plan.setBillingPeriod(updatedPlan.getBillingPeriod());
        plan.setPrice(updatedPlan.getPrice());
        plan.setCurrency(updatedPlan.getCurrency());
        plan.setActive(updatedPlan.isActive());
        plan.setTrialDays(updatedPlan.getTrialDays());
        plan.setUpdatedAt(LocalDateTime.now());
        return planRepository.save(plan);
    }

    @Transactional
    public void deletePlan(Long id) {
        SubscriptionPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Plan not found"));
        plan.setActive(false);
        plan.setUpdatedAt(LocalDateTime.now());
        planRepository.save(plan);
        log.info("Plan {} deactivated", id);
    }

    // ============================================================
    // SUBSCRIPTION MANAGEMENT
    // ============================================================

    @Transactional
    public CustomerSubscription createSubscription(Long customerId, String customerEmail,
                                                   Long merchantId, Long planId) {
        Long tenantId = TenantContext.getRequiredTenantId();

        SubscriptionPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("Plan not found"));

        // Check for existing active subscription to this plan
        List<CustomerSubscription> existing = subscriptionRepository
                .findByCustomerIdAndPlanIdAndStatus(customerId, planId, CustomerSubscription.SubscriptionStatus.ACTIVE)
                .stream().toList();

        if (!existing.isEmpty()) {
            throw new RuntimeException("Customer already has an active subscription to this plan");
        }

        CustomerSubscription subscription = createSubscriptionInternal(customerId, customerEmail, merchantId, planId);
        
        log.info("✅ Subscription created: {} for customer {}", subscription.getId(), customerEmail);
        return subscription;
    }

    private CustomerSubscription createSubscriptionInternal(Long customerId, String customerEmail,
                                                            Long merchantId, Long planId) {
        Long tenantId = TenantContext.getRequiredTenantId();
        
        SubscriptionPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("Plan not found"));

        CustomerSubscription subscription = new CustomerSubscription();
        subscription.setPlan(plan);
        subscription.setCustomerId(customerId);
        subscription.setCustomerEmail(customerEmail);
        subscription.setMerchantId(merchantId);
        subscription.setTenantId(tenantId);

        LocalDateTime now = LocalDateTime.now();
        subscription.setStartDate(now);

        if (plan.getTrialDays() != null && plan.getTrialDays() > 0) {
            subscription.setStatus(CustomerSubscription.SubscriptionStatus.TRIALING);
            subscription.setTrialEndDate(now.plusDays(plan.getTrialDays()));
            subscription.setNextBillingDate(now.plusDays(plan.getTrialDays()));
        } else {
            subscription.setStatus(CustomerSubscription.SubscriptionStatus.ACTIVE);
            subscription.setNextBillingDate(now.plusMonths(plan.getMonthsInPeriod()));
        }

        CustomerSubscription saved = subscriptionRepository.save(subscription);

        // Send confirmation notification to customer
        notificationService.createNotification(
                customerId,
                "SUBSCRIPTION_CREATED",
                "Subscription Created",
                "You have subscribed to " + plan.getName() + " (" + plan.getBillingPeriod() + ")",
                "/subscriptions/my"
        );

        // Send notification to merchant
        notificationService.createNotification(
                merchantId,
                "NEW_SUBSCRIPTION",
                "New Subscription",
                "Customer " + customerEmail + " subscribed to " + plan.getName(),
                "/merchant/subscriptions"
        );

        return saved;
    }

    @Transactional
    public CustomerSubscription upgradeSubscription(Long subscriptionId, Long newPlanId) {
        CustomerSubscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("Subscription not found"));

        SubscriptionPlan oldPlan = subscription.getPlan();
        SubscriptionPlan newPlan = planRepository.findById(newPlanId)
                .orElseThrow(() -> new RuntimeException("New plan not found"));

        if (oldPlan.getId().equals(newPlanId)) {
            throw new RuntimeException("Already subscribed to this plan");
        }

        // Calculate prorated amount
        LocalDateTime now = LocalDateTime.now();
        BigDecimal proratedAmount = calculateProratedCharge(subscription, newPlan);

        // Create prorated invoice if amount > 0
        if (proratedAmount.compareTo(BigDecimal.ZERO) > 0) {
            Invoice invoice = invoiceService.createInvoice(
                    proratedAmount,
                    "Subscription Upgrade: " + oldPlan.getName() + " → " + newPlan.getName(),
                    subscription.getCustomerEmail(),
                    subscription.getMerchantId(),
                    false,
                    newPlan.getCurrency()
            );
            log.info("✅ Prorated invoice #{} created for upgrade", invoice.getId());
        }

        // Update subscription
        subscription.setPlan(newPlan);
        subscription.setNextBillingDate(now.plusMonths(newPlan.getMonthsInPeriod()));
        subscription.setUpdatedAt(now);

        CustomerSubscription updated = subscriptionRepository.save(subscription);

        // Send notifications
        notificationService.createNotification(
                subscription.getCustomerId(),
                "SUBSCRIPTION_UPGRADED",
                "Subscription Upgraded",
                "Your subscription has been upgraded to " + newPlan.getName(),
                "/subscriptions/my"
        );

        notificationService.createNotification(
                subscription.getMerchantId(),
                "SUBSCRIPTION_UPGRADED_MERCHANT",
                "Subscription Upgraded",
                "Customer " + subscription.getCustomerEmail() + " upgraded to " + newPlan.getName(),
                "/merchant/subscriptions"
        );

        log.info("⬆️ Subscription {} upgraded from {} to {}", subscriptionId, oldPlan.getName(), newPlan.getName());
        return updated;
    }

    @Transactional
    public CustomerSubscription downgradeSubscription(Long subscriptionId, Long newPlanId) {
        CustomerSubscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("Subscription not found"));

        SubscriptionPlan newPlan = planRepository.findById(newPlanId)
                .orElseThrow(() -> new RuntimeException("New plan not found"));

        // Update subscription (no prorated charge for downgrade)
        subscription.setPlan(newPlan);
        subscription.setNextBillingDate(LocalDateTime.now().plusMonths(newPlan.getMonthsInPeriod()));
        subscription.setUpdatedAt(LocalDateTime.now());

        CustomerSubscription updated = subscriptionRepository.save(subscription);

        // Send notifications
        notificationService.createNotification(
                subscription.getCustomerId(),
                "SUBSCRIPTION_DOWNGRADED",
                "Subscription Downgraded",
                "Your subscription has been downgraded to " + newPlan.getName(),
                "/subscriptions/my"
        );

        log.info("⬇️ Subscription {} downgraded to {}", subscriptionId, newPlan.getName());
        return updated;
    }

    private BigDecimal calculateProratedCharge(CustomerSubscription subscription, SubscriptionPlan newPlan) {
        SubscriptionPlan oldPlan = subscription.getPlan();

        // If new plan is cheaper, no charge
        if (newPlan.getPrice().compareTo(oldPlan.getPrice()) <= 0) {
            return BigDecimal.ZERO;
        }

        // Calculate remaining days in current billing cycle
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextBilling = subscription.getNextBillingDate();
        long daysRemaining = java.time.Duration.between(now, nextBilling).toDays();
        if (daysRemaining <= 0) return BigDecimal.ZERO;

        long totalDays = oldPlan.getMonthsInPeriod() * 30; // Approximate
        BigDecimal dailyRate = oldPlan.getPrice().divide(BigDecimal.valueOf(totalDays), 4, RoundingMode.HALF_UP);
        BigDecimal unusedValue = dailyRate.multiply(BigDecimal.valueOf(daysRemaining));

        // Get new plan price difference
        BigDecimal priceDiff = newPlan.getPrice().subtract(oldPlan.getPrice());

        // Prorated amount = unused value + price diff
        return unusedValue.add(priceDiff).setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional
    public void cancelSubscription(Long subscriptionId) {
        CustomerSubscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("Subscription not found"));

        subscription.setStatus(CustomerSubscription.SubscriptionStatus.CANCELLED);
        subscription.setCancelledAt(LocalDateTime.now());
        subscription.setUpdatedAt(LocalDateTime.now());
        subscriptionRepository.save(subscription);

        notificationService.createNotification(
                subscription.getCustomerId(),
                "SUBSCRIPTION_CANCELLED",
                "Subscription Cancelled",
                "Your subscription has been cancelled",
                "/subscriptions/my"
        );

        log.info("❌ Subscription {} cancelled", subscriptionId);
    }

    // ============================================================
    // BILLING & RETRY LOGIC
    // ============================================================

    @Transactional
    public void processBilling() {
        log.info("🔄 Processing subscription billing...");
        LocalDateTime now = LocalDateTime.now();

        List<CustomerSubscription> dueSubscriptions = subscriptionRepository
                .findActiveSubscriptionsDueForBilling(now);

        if (dueSubscriptions.isEmpty()) {
            log.debug("No subscriptions due for billing");
            return;
        }

        int successCount = 0;
        int failCount = 0;

        for (CustomerSubscription subscription : dueSubscriptions) {
            try {
                processSingleBilling(subscription);
                successCount++;
            } catch (Exception e) {
                failCount++;
                log.error("❌ Failed to process billing for subscription {}: {}",
                        subscription.getId(), e.getMessage());

                // Create retry entry
                createRetry(subscription, e.getMessage());
            }
        }

        log.info("✅ Billing completed: {} successful, {} failed", successCount, failCount);
    }

    @Transactional
    public void processSingleBilling(CustomerSubscription subscription) {
        SubscriptionPlan plan = subscription.getPlan();

        // Create invoice
        Invoice invoice = invoiceService.createInvoice(
                plan.getPrice(),
                "Subscription: " + plan.getName() + " (" + plan.getBillingPeriod() + ")",
                subscription.getCustomerEmail(),
                subscription.getMerchantId(),
                false,
                plan.getCurrency()
        );

        // Update subscription
        subscription.setLastBillingDate(LocalDateTime.now());
        subscription.setNextBillingDate(LocalDateTime.now().plusMonths(plan.getMonthsInPeriod()));
        subscription.setUpdatedAt(LocalDateTime.now());
        subscriptionRepository.save(subscription);

        // Send billing confirmation
        notificationService.createNotification(
                subscription.getCustomerId(),
                "BILLING_CONFIRMATION",
                "Billing Confirmation",
                "Your subscription " + plan.getName() + " has been billed: " + plan.getPrice() + " " + plan.getCurrency(),
                "/invoices/" + invoice.getId()
        );

        log.info("✅ Billed subscription {}: Invoice #{} created", subscription.getId(), invoice.getId());
    }

    @Transactional
    public void createRetry(CustomerSubscription subscription, String errorMessage) {
        PaymentRetry retry = new PaymentRetry();
        retry.setSubscriptionId(subscription.getId());
        retry.setAttemptNumber(1);
        retry.setMaxAttempts(3);
        retry.setStatus(PaymentRetry.RetryStatus.PENDING);
        retry.setErrorMessage(errorMessage);
        retry.setNextAttemptAt(LocalDateTime.now().plusHours(24));
        paymentRetryRepository.save(retry);

        log.info("⏳ Retry created for subscription {}: attempt 1/3", subscription.getId());
    }

    @Scheduled(cron = "0 0 6 * * *")  // Daily at 6 AM
    public void scheduledBilling() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) tenantId = 1L;
        TenantContext.setTenantId(tenantId);

        try {
            processBilling();
            processRetries();
        } finally {
            TenantContext.clear();
        }
    }

    @Scheduled(cron = "0 30 6 * * *")  // Daily at 6:30 AM
    public void processRetries() {
        log.info("🔄 Processing payment retries...");

        List<PaymentRetry> retries = paymentRetryRepository.findByStatusAndNextAttemptAtBefore(
                PaymentRetry.RetryStatus.PENDING, LocalDateTime.now()
        );

        for (PaymentRetry retry : retries) {
            try {
                CustomerSubscription subscription = subscriptionRepository.findById(retry.getSubscriptionId())
                        .orElse(null);

                if (subscription == null || subscription.getStatus() != CustomerSubscription.SubscriptionStatus.ACTIVE) {
                    retry.setStatus(PaymentRetry.RetryStatus.CANCELLED);
                    paymentRetryRepository.save(retry);
                    continue;
                }

                processSingleBilling(subscription);
                retry.setStatus(PaymentRetry.RetryStatus.SUCCESS);
                paymentRetryRepository.save(retry);
                log.info("✅ Retry successful for subscription {}", retry.getSubscriptionId());

            } catch (Exception e) {
                retry.incrementAttempt();
                if (retry.canRetry()) {
                    retry.setNextAttemptAt(LocalDateTime.now().plusHours(24 * retry.getAttemptNumber()));
                } else {
                    retry.setStatus(PaymentRetry.RetryStatus.FAILED);
                    // Notify merchant
                    notificationService.createNotification(
                            1L, // Merchant ID
                            "PAYMENT_RETRY_FAILED",
                            "Payment Retry Failed",
                            "All retry attempts failed for subscription #" + retry.getSubscriptionId(),
                            "/merchant/subscriptions"
                    );
                }
                retry.setErrorMessage(e.getMessage());
                paymentRetryRepository.save(retry);
                log.error("❌ Retry failed for subscription {}: {}", retry.getSubscriptionId(), e.getMessage());
            }
        }
    }

    // ============================================================
    // QUERY METHODS
    // ============================================================

    public CustomerSubscription getSubscriptionById(Long id) {
        return subscriptionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Subscription not found"));
    }

    public List<CustomerSubscription> getCustomerSubscriptions(Long customerId) {
        return subscriptionRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    public List<CustomerSubscription> getMerchantSubscriptions(Long merchantId) {
        return subscriptionRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    public Page<CustomerSubscription> getMerchantSubscriptionsPaginated(Long merchantId, Pageable pageable) {
        return subscriptionRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId, pageable);
    }

    public Map<String, Object> getMerchantStats(Long merchantId) {
        Map<String, Object> stats = new HashMap<>();

        try {
            long activeCount = subscriptionRepository.countByMerchantIdAndStatus(
                    merchantId, CustomerSubscription.SubscriptionStatus.ACTIVE
            );
            long trialingCount = subscriptionRepository.countByMerchantIdAndStatus(
                    merchantId, CustomerSubscription.SubscriptionStatus.TRIALING
            );
            long pendingRequests = requestRepository.countByMerchantIdAndStatus(
                    merchantId, SubscriptionRequest.RequestStatus.PENDING
            );

            List<CustomerSubscription> subscriptions = subscriptionRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId);
            BigDecimal totalRevenue = subscriptions.stream()
                    .filter(s -> s.getStatus() == CustomerSubscription.SubscriptionStatus.ACTIVE)
                    .map(s -> s.getPlan().getPrice())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            stats.put("activeSubscriptions", activeCount);
            stats.put("trialingSubscriptions", trialingCount);
            stats.put("totalSubscriptions", (long) subscriptions.size());
            stats.put("monthlyRevenue", totalRevenue);
            stats.put("annualRevenue", totalRevenue.multiply(BigDecimal.valueOf(12)));
            stats.put("pendingRequests", pendingRequests);

        } catch (Exception e) {
            log.error("Error getting merchant stats: {}", e.getMessage());
            stats.put("activeSubscriptions", 0L);
            stats.put("trialingSubscriptions", 0L);
            stats.put("totalSubscriptions", 0L);
            stats.put("monthlyRevenue", BigDecimal.ZERO);
            stats.put("annualRevenue", BigDecimal.ZERO);
            stats.put("pendingRequests", 0L);
        }

        return stats;
    }
}