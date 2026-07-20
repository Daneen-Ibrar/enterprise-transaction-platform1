package com.enterprise.integration;

import com.enterprise.identity.AppUser;
import com.enterprise.reliability.DlqEntry;
import com.enterprise.reliability.DlqEntryRepository;
import com.enterprise.reliability.DlqScheduler;
import com.enterprise.tenant.Tenant;
import com.enterprise.util.TestDataBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
public class DlqSchedulerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestDataBuilder testDataBuilder;

    @Autowired
    private DlqEntryRepository dlqEntryRepository;

    @Autowired
    private DlqScheduler dlqScheduler;

    private Tenant tenant;
    private AppUser admin;

    @BeforeEach
    void setUp() {
        tenant = testDataBuilder.createTenant("DLQTest");
        admin = testDataBuilder.createUser("admin@dlq.com", "ADMIN", tenant);
        dlqEntryRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        dlqEntryRepository.deleteAll();
        testDataBuilder.cleanup();
    }

    @Test
    void shouldProcessPendingDlqEntries() {
        DlqEntry entry1 = new DlqEntry();
        entry1.setOperationType("PAYMENT");
        entry1.setPayload("{\"test\":\"data\"}");
        entry1.setFailureReason("Test failure");
        entry1.setStatus("PENDING");
        entry1.setFailureCount(1);
        dlqEntryRepository.save(entry1);

        DlqEntry entry2 = new DlqEntry();
        entry2.setOperationType("REFUND");
        entry2.setPayload("{\"refund\":\"test\"}");
        entry2.setFailureReason("Another failure");
        entry2.setStatus("PENDING");
        entry2.setFailureCount(1);
        dlqEntryRepository.save(entry2);

        dlqScheduler.processDlq();

        List<DlqEntry> entries = dlqEntryRepository.findAll();
        assertThat(entries).hasSize(2);
        assertThat(entries).extracting("status").containsOnly("PENDING");
    }

    @Test
    void shouldNotProcessFailedEntries() {
        DlqEntry entry = new DlqEntry();
        entry.setOperationType("PAYMENT");
        entry.setPayload("{\"test\":\"data\"}");
        entry.setFailureReason("Permanent failure");
        entry.setStatus("PENDING");
        entry.setFailureCount(3);
        dlqEntryRepository.save(entry);

        dlqScheduler.processDlq();

        DlqEntry processed = dlqEntryRepository.findAll().get(0);
        assertThat(processed.getStatus()).isEqualTo("FAILED");
    }

    @Test
    void shouldProcessOnlyPendingEntries() {
        DlqEntry pending = new DlqEntry();
        pending.setOperationType("PAYMENT");
        pending.setPayload("{}");
        pending.setFailureReason("Test");
        pending.setStatus("PENDING");
        pending.setFailureCount(1);
        dlqEntryRepository.save(pending);

        DlqEntry resolved = new DlqEntry();
        resolved.setOperationType("REFUND");
        resolved.setPayload("{}");
        resolved.setFailureReason("Already resolved");
        resolved.setStatus("RESOLVED");
        resolved.setFailureCount(1);
        dlqEntryRepository.save(resolved);

        DlqEntry failed = new DlqEntry();
        failed.setOperationType("PAYMENT");
        failed.setPayload("{}");
        failed.setFailureReason("Permanent");
        failed.setStatus("FAILED");
        failed.setFailureCount(3);
        dlqEntryRepository.save(failed);

        dlqScheduler.processDlq();

        List<DlqEntry> entries = dlqEntryRepository.findAll();
        assertThat(entries).hasSize(3);
        assertThat(entries.stream().filter(e -> "PENDING".equals(e.getStatus()))).hasSize(1);
    }
}