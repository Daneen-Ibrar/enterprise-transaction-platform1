package com.enterprise.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OutboxEvent createEvent(String eventType, Long aggregateId, String aggregateType,
                                   Object payload, Map<String, String> headers) {
        try {
            OutboxEvent event = new OutboxEvent();
            event.setEventType(eventType);
            event.setAggregateId(aggregateId);
            event.setAggregateType(aggregateType);
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setHeaders(headers != null ? objectMapper.writeValueAsString(headers) : null);
            event.setStatus(OutboxStatus.PENDING);
            event.setNextAttemptAt(LocalDateTime.now());

            OutboxEvent saved = outboxRepository.save(event);
            log.info("Outbox event created: type={}, aggregateId={}, id={}", eventType, aggregateId, saved.getId());
            return saved;
        } catch (Exception e) {
            log.error("Failed to create outbox event", e);
            throw new RuntimeException("Failed to create outbox event", e);
        }
    }

    @Transactional
    public void createWebhookEvent(Long aggregateId, String aggregateType,
                                   Object payload, Map<String, String> headers) {
        createEvent("WEBHOOK_DELIVERY", aggregateId, aggregateType, payload, headers);
    }
}