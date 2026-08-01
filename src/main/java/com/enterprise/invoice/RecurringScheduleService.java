package com.enterprise.invoice;

import com.enterprise.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class RecurringScheduleService {

    private static final Logger log = LoggerFactory.getLogger(RecurringScheduleService.class);

    private final RecurringScheduleRepository scheduleRepository;
    private final InvoiceService invoiceService;

    public RecurringScheduleService(RecurringScheduleRepository scheduleRepository,
                                    InvoiceService invoiceService) {
        this.scheduleRepository = scheduleRepository;
        this.invoiceService = invoiceService;
    }

    @Transactional
    public RecurringSchedule createSchedule(RecurringSchedule schedule) {
        schedule.setTenantId(TenantContext.getRequiredTenantId());
        schedule.setNextRunDate(schedule.getStartDate());
        schedule.setActive(true);
        return scheduleRepository.save(schedule);
    }

    @Transactional
    public RecurringSchedule updateSchedule(RecurringSchedule incomingSchedule) {
        int retries = 3;
        while (retries > 0) {
            try {
                // Load the latest version from the database
                RecurringSchedule existing = scheduleRepository.findById(incomingSchedule.getId())
                        .orElseThrow(() -> new RuntimeException("Schedule not found"));

                // Copy allowed fields (do NOT copy version or nextRunDate)
                existing.setCustomerEmail(incomingSchedule.getCustomerEmail());
                existing.setAmount(incomingSchedule.getAmount());
                existing.setCurrency(incomingSchedule.getCurrency());
                existing.setDescription(incomingSchedule.getDescription());
                existing.setFrequency(incomingSchedule.getFrequency());
                existing.setStartDate(incomingSchedule.getStartDate());
                existing.setEndDate(incomingSchedule.getEndDate());
                existing.setUpdatedAt(LocalDateTime.now());

                // Save – Hibernate will check version and increment it
                return scheduleRepository.save(existing);

            } catch (ObjectOptimisticLockingFailureException e) {
                retries--;
                if (retries == 0) {
                    log.error("Optimistic locking failure for schedule {} after retries", incomingSchedule.getId(), e);
                    throw e;
                }
                log.warn("Optimistic locking failure for schedule {}, retrying... ({} retries left)", incomingSchedule.getId(), retries);
                try {
                    // Wait briefly for the concurrent transaction to finish
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during retry", ie);
                }
            }
        }
        throw new RuntimeException("Failed to update schedule after retries");
    }

    @Transactional
    public void toggleActive(Long id) {
        RecurringSchedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Schedule not found"));
        schedule.setActive(!schedule.isActive());
        schedule.setUpdatedAt(LocalDateTime.now());
        scheduleRepository.save(schedule);
    }

    @Transactional
    public void deleteSchedule(Long id) {
        scheduleRepository.deleteById(id);
    }

    public List<RecurringSchedule> getSchedulesForMerchant(Long merchantId) {
        return scheduleRepository.findByMerchantIdOrderByNextRunDateAsc(merchantId);
    }

    public RecurringSchedule getSchedule(Long id) {
        return scheduleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Schedule not found"));
    }

    @Transactional
    public int processDueSchedules() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            log.warn("No tenant context, skipping recurring schedule processing");
            return 0;
        }

        LocalDate today = LocalDate.now();
        List<RecurringSchedule> schedules = scheduleRepository.findActiveSchedulesDueForDate(today, tenantId);

        int created = 0;
        for (RecurringSchedule schedule : schedules) {
            try {
                Invoice invoice = invoiceService.createInvoice(
                        schedule.getAmount(),
                        schedule.getDescription(),
                        schedule.getCustomerEmail(),
                        schedule.getMerchantId(),
                        false,
                        schedule.getCurrency()
                );

                LocalDate nextRun = schedule.getNextRunDate();
                switch (schedule.getFrequency()) {
                    case DAILY -> nextRun = nextRun.plusDays(1);
                    case WEEKLY -> nextRun = nextRun.plusWeeks(1);
                    case MONTHLY -> nextRun = nextRun.plusMonths(1);
                }
                if (schedule.getEndDate() != null && nextRun.isAfter(schedule.getEndDate())) {
                    schedule.setActive(false);
                }
                schedule.setNextRunDate(nextRun);
                schedule.setUpdatedAt(LocalDateTime.now());
                scheduleRepository.save(schedule);

                created++;
                log.info("Created recurring invoice #{} for schedule {}", invoice.getId(), schedule.getId());

            } catch (Exception e) {
                log.error("Failed to process recurring schedule {}: {}", schedule.getId(), e.getMessage(), e);
            }
        }

        if (created > 0) {
            log.info("Created {} recurring invoices for tenant {}", created, tenantId);
        }
        return created;
    }
}