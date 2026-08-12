package com.enterprise.xero.service;

import com.enterprise.invoice.Invoice;
import com.enterprise.invoice.InvoiceRepository;
import com.enterprise.tenant.TenantContext;
import com.enterprise.xero.entity.XeroToken;
import com.xero.api.ApiClient;
import com.xero.api.client.AccountingApi;
import com.xero.models.accounting.Account;
import com.xero.models.accounting.AccountType;
import com.xero.models.accounting.Contact;
import com.xero.models.accounting.Invoices;
import com.xero.models.accounting.LineItem;
import com.xero.models.accounting.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.UUID;

@Service
public class XeroInvoiceService {

    private static final Logger log = LoggerFactory.getLogger(XeroInvoiceService.class);

    private final XeroOAuthService xeroOAuthService;
    private final ApiClient defaultClient;
    private final InvoiceRepository invoiceRepository;

    private static final String FAKE_XERO_ID = "00000000-0000-0000-0000-000000000000";

    public XeroInvoiceService(XeroOAuthService xeroOAuthService, InvoiceRepository invoiceRepository) {
        this.xeroOAuthService = xeroOAuthService;
        this.invoiceRepository = invoiceRepository;
        this.defaultClient = new ApiClient();
    }

    // ===== SYNC INVOICE TO XERO =====
    @Transactional
    public String syncInvoiceToXero(Invoice invoice) throws Exception {
        if (invoice.getXeroInvoiceId() != null && 
            !invoice.getXeroInvoiceId().isEmpty() && 
            !FAKE_XERO_ID.equals(invoice.getXeroInvoiceId())) {
            log.info("⏭️ Invoice {} already synced to Xero with ID: {}", 
                    invoice.getId(), invoice.getXeroInvoiceId());
            return invoice.getXeroInvoiceId();
        }

        if (FAKE_XERO_ID.equals(invoice.getXeroInvoiceId())) {
            log.warn("⚠️ Invoice {} has fake Xero ID, clearing", invoice.getId());
            invoice.setXeroInvoiceId(null);
            invoice.setWebhookUrl(null);
            invoiceRepository.save(invoice);
        }

        Long tenantId = TenantContext.getRequiredTenantId();
        XeroToken token = xeroOAuthService.getValidToken(tenantId);

        if (token == null) {
            log.warn("No Xero token found for tenant: {}", tenantId);
            return null;
        }

        AccountingApi accountingApi = AccountingApi.getInstance(defaultClient);

        com.xero.models.accounting.Invoice xeroInvoice = new com.xero.models.accounting.Invoice();
        xeroInvoice.setType(com.xero.models.accounting.Invoice.TypeEnum.ACCREC);
        
        try {
            xeroInvoice.setStatus(com.xero.models.accounting.Invoice.StatusEnum.AUTHORISED);
        } catch (Exception e) {
            log.warn("Could not set status, using Xero default");
        }

        Contact contact = new Contact();
        String contactName = invoice.getCustomerEmail();
        if (contactName == null || contactName.isEmpty()) {
            contactName = "Unknown Customer";
        }
        contact.setName(contactName);
        xeroInvoice.setContact(contact);

        String todayXeroDate = formatToXeroDate(LocalDate.now());
        String dueXeroDate = formatToXeroDate(LocalDate.now().plusDays(30));
        
        xeroInvoice.setDate(todayXeroDate);
        xeroInvoice.setDueDate(dueXeroDate);
        xeroInvoice.setReference("INV-" + invoice.getId());

        try {
            xeroInvoice.setCurrencyCode(
                com.xero.models.accounting.CurrencyCode.fromValue(invoice.getCurrency())
            );
        } catch (Exception e) {
            log.warn("Invalid currency code: {}, defaulting to GBP", invoice.getCurrency());
            xeroInvoice.setCurrencyCode(com.xero.models.accounting.CurrencyCode.GBP);
        }

        LineItem lineItem = new LineItem();
        String description = invoice.getDescription();
        if (description == null || description.isEmpty()) {
            description = "Invoice #" + invoice.getId();
        }
        lineItem.setDescription(description);
        lineItem.setQuantity(1.0);
        
        double amount = invoice.getAmount().doubleValue();
        if (amount <= 0) {
            log.warn("⚠️ Invoice {} has invalid amount: {}, setting to 1.00", invoice.getId(), amount);
            amount = 1.00;
        }
        lineItem.setUnitAmount(amount);
        lineItem.setAccountCode("200");
        xeroInvoice.setLineItems(Collections.singletonList(lineItem));

        Invoices invoicesWrapper = new Invoices();
        invoicesWrapper.setInvoices(Collections.singletonList(xeroInvoice));

        String idempotencyKey = "inv-" + invoice.getId() + "-" + UUID.randomUUID();

        try {
            log.info("📤 Syncing invoice {} to Xero: Reference={}, Amount={}", 
                    invoice.getId(), xeroInvoice.getReference(), invoice.getAmount());

            Invoices response = accountingApi.createInvoices(
                    token.getAccessToken(),
                    token.getXeroTenantId(),
                    invoicesWrapper,
                    false,
                    null,
                    idempotencyKey,
                    null
            );

            if (response == null || response.getInvoices() == null || response.getInvoices().isEmpty()) {
                log.error("❌ No invoice returned from Xero");
                return null;
            }

            com.xero.models.accounting.Invoice createdInvoice = response.getInvoices().get(0);
            
            if (createdInvoice.getInvoiceID() == null) {
                log.error("❌ Xero returned invoice with null ID");
                return null;
            }

            String xeroInvoiceId = createdInvoice.getInvoiceID().toString();
            
            if (FAKE_XERO_ID.equals(xeroInvoiceId)) {
                log.error("❌ Xero returned fake ID");
                return null;
            }

            invoice.setXeroInvoiceId(xeroInvoiceId);
            invoiceRepository.save(invoice);
            
            log.info("✅ Invoice {} synced to Xero with ID: {}", invoice.getId(), xeroInvoiceId);
            return xeroInvoiceId;

        } catch (com.xero.api.XeroBadRequestException e) {
            log.error("❌ Xero Bad Request: {}", e.getMessage());
            if (e.getElements() != null && !e.getElements().isEmpty()) {
                for (com.xero.models.accounting.Element element : e.getElements()) {
                    if (element.getValidationErrors() != null) {
                        for (com.xero.models.accounting.ValidationError error : element.getValidationErrors()) {
                            log.error("   Validation error: {}", error.getMessage());
                        }
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.error("❌ Failed to sync invoice: {}", e.getMessage(), e);
            return null;
        }
    }

    // ===== SYNC PAYMENT TO XERO =====
    @Transactional
    public String syncPaymentToXero(Invoice invoice, Long transactionId) {
        try {
            log.info("📤 Syncing payment for invoice {} to Xero: Amount={}, Invoice ID={}", 
                    invoice.getId(), invoice.getAmount(), invoice.getXeroInvoiceId());

            if (invoice.getXeroInvoiceId() == null || invoice.getXeroInvoiceId().isEmpty()) {
                log.warn("⚠️ Invoice {} has no Xero ID, cannot sync payment", invoice.getId());
                String xeroId = syncInvoiceToXero(invoice);
                if (xeroId == null) {
                    log.error("❌ Could not sync invoice {} to Xero, payment not synced", invoice.getId());
                    return null;
                }
                invoice.setXeroInvoiceId(xeroId);
                invoiceRepository.save(invoice);
            }

            Long tenantId = TenantContext.getRequiredTenantId();
            XeroToken token = xeroOAuthService.getValidToken(tenantId);

            if (token == null) {
                log.warn("No Xero token found for tenant: {}", tenantId);
                return null;
            }

            AccountingApi accountingApi = AccountingApi.getInstance(defaultClient);

            // ✅ Get account for payment
            Account account = getAccountForPayment(accountingApi, token);
            if (account == null) {
                log.error("❌ No account found for payment");
                return null;
            }

            Payment payment = new Payment();
            
            com.xero.models.accounting.Invoice invoiceRef = new com.xero.models.accounting.Invoice();
            invoiceRef.setInvoiceID(java.util.UUID.fromString(invoice.getXeroInvoiceId()));
            payment.setInvoice(invoiceRef);
            
            payment.setAmount(invoice.getAmount().doubleValue());
            
            String paymentDate = formatToXeroDate(LocalDate.now());
            payment.setDate(paymentDate);
            
            payment.setAccount(account);

            try {
                payment.setCurrencyRate(1.0);
            } catch (Exception e) {
                // Ignore if not supported
            }

            log.info("📤 Sending payment to Xero: Amount={}, Date={}, Account Type={}, Code={}", 
                    invoice.getAmount(), paymentDate, account.getType(), account.getCode());

            com.xero.models.accounting.Payments response = accountingApi.createPayment(
                    token.getAccessToken(),
                    token.getXeroTenantId(),
                    payment,
                    null
                );

            if (response == null || response.getPayments() == null || response.getPayments().isEmpty()) {
                log.error("❌ No payment returned from Xero");
                return null;
            }

            String paymentId = response.getPayments().get(0).getPaymentID().toString();
            log.info("✅ Payment {} synced to Xero for invoice {} with ID: {}", 
                    transactionId, invoice.getId(), paymentId);

            return paymentId;

        } catch (com.xero.api.XeroBadRequestException e) {
            log.error("❌ Xero Bad Request for payment: {}", e.getMessage());
            if (e.getElements() != null && !e.getElements().isEmpty()) {
                for (com.xero.models.accounting.Element element : e.getElements()) {
                    if (element.getValidationErrors() != null) {
                        for (com.xero.models.accounting.ValidationError error : element.getValidationErrors()) {
                            log.error("   Validation error: {}", error.getMessage());
                        }
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.error("❌ Failed to sync payment for invoice {}: {}", invoice.getId(), e.getMessage(), e);
            return null;
        }
    }

    // ===== GET ACCOUNT FOR PAYMENT - SUPPORTS ALL ACCOUNT TYPES =====
    private Account getAccountForPayment(AccountingApi accountingApi, XeroToken token) {
        try {
            // 1️⃣ Try BANK account
            com.xero.models.accounting.Accounts accounts = accountingApi.getAccounts(
                token.getAccessToken(),
                token.getXeroTenantId(),
                null,
                "Type==\"BANK\"",
                null
            );
            
            if (accounts != null && accounts.getAccounts() != null && !accounts.getAccounts().isEmpty()) {
                Account bankAccount = accounts.getAccounts().get(0);
                log.info("✅ Found BANK account: Code={}, AccountID={}", 
                        bankAccount.getCode(), bankAccount.getAccountID());
                return bankAccount;
            }
            
            // 2️⃣ Try CURRENT_ASSET account (your 090 account)
            com.xero.models.accounting.Accounts currentAssetAccounts = accountingApi.getAccounts(
                token.getAccessToken(),
                token.getXeroTenantId(),
                null,
                "Type==\"CURRENT_ASSET\"",
                null
            );
            
            if (currentAssetAccounts != null && currentAssetAccounts.getAccounts() != null && !currentAssetAccounts.getAccounts().isEmpty()) {
                Account currentAssetAccount = currentAssetAccounts.getAccounts().get(0);
                log.info("✅ Found CURRENT_ASSET account: Code={}, AccountID={}", 
                        currentAssetAccount.getCode(), currentAssetAccount.getAccountID());
                return currentAssetAccount;
            }
            
            // 3️⃣ Try account code 090 specifically
            com.xero.models.accounting.Accounts account090 = accountingApi.getAccounts(
                token.getAccessToken(),
                token.getXeroTenantId(),
                null,
                "Code==\"090\"",
                null
            );
            
            if (account090 != null && account090.getAccounts() != null && !account090.getAccounts().isEmpty()) {
                Account account = account090.getAccounts().get(0);
                log.info("✅ Found account 090: Code=090, AccountID={}, Type={}", 
                        account.getAccountID(), account.getType());
                return account;
            }
            
            // 4️⃣ Try account code 200 (Revenue)
            com.xero.models.accounting.Accounts revenueAccounts = accountingApi.getAccounts(
                token.getAccessToken(),
                token.getXeroTenantId(),
                null,
                "Code==\"200\"",
                null
            );
            
            if (revenueAccounts != null && revenueAccounts.getAccounts() != null && !revenueAccounts.getAccounts().isEmpty()) {
                Account revenueAccount = revenueAccounts.getAccounts().get(0);
                log.info("✅ Using Revenue/200 account for payment: Code=200, AccountID={}", 
                        revenueAccount.getAccountID());
                return revenueAccount;
            }
            
            // 5️⃣ Try any active account
            com.xero.models.accounting.Accounts allAccounts = accountingApi.getAccounts(
                token.getAccessToken(),
                token.getXeroTenantId(),
                null,
                "Status==\"ACTIVE\"",
                null
            );
            
            if (allAccounts != null && allAccounts.getAccounts() != null && !allAccounts.getAccounts().isEmpty()) {
                Account firstAccount = allAccounts.getAccounts().get(0);
                log.info("✅ Using fallback account: Code={}, AccountID={}, Type={}", 
                        firstAccount.getCode(), firstAccount.getAccountID(), firstAccount.getType());
                return firstAccount;
            }
            
            // 6️⃣ Ultimate fallback: Create account in memory
            log.warn("⚠️ No accounts found, creating default account in memory");
            Account defaultAccount = new Account();
            defaultAccount.setCode("DEFAULT");
            defaultAccount.setName("Default Account");
            defaultAccount.setType(AccountType.BANK);
            defaultAccount.setAccountID(UUID.randomUUID());
            return defaultAccount;
            
        } catch (Exception e) {
            log.error("❌ Failed to fetch accounts: {}", e.getMessage());
            Account defaultAccount = new Account();
            defaultAccount.setCode("DEFAULT");
            defaultAccount.setName("Default Account");
            defaultAccount.setType(AccountType.BANK);
            defaultAccount.setAccountID(UUID.randomUUID());
            return defaultAccount;
        }
    }

    // ===== DATE FORMATTER =====
    private String formatToXeroDate(LocalDate date) {
        long epochMillis = date.atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000;
        return "/Date(" + epochMillis + ")/";
    }

    // ===== GET INVOICE FROM XERO =====
    public com.xero.models.accounting.Invoice getInvoiceFromXero(String xeroInvoiceId) throws Exception {
        Long tenantId = TenantContext.getRequiredTenantId();
        XeroToken token = xeroOAuthService.getValidToken(tenantId);

        if (token == null) {
            log.warn("No Xero token found for tenant: {}", tenantId);
            return null;
        }

        AccountingApi accountingApi = AccountingApi.getInstance(defaultClient);

        try {
            Invoices response = accountingApi.getInvoice(
                    token.getAccessToken(),
                    token.getXeroTenantId(),
                    java.util.UUID.fromString(xeroInvoiceId),
                    null
            );

            if (response == null || response.getInvoices() == null || response.getInvoices().isEmpty()) {
                return null;
            }

            return response.getInvoices().get(0);

        } catch (Exception e) {
            log.error("❌ Failed to get invoice from Xero: {}", e.getMessage());
            throw e;
        }
    }
}