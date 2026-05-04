package com.tontine.service.impl;

import com.tontine.entity.AuditLog;
import com.tontine.repository.AuditLogRepository;
import com.tontine.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository auditLogRepository;

    @Async("auditExecutor")
    @Override
    public void log(Long userId, String telephone, String action, boolean succes, String details) {
        log(userId, telephone, action, succes, null, details);
    }

    @Async("auditExecutor")
    @Override
    public void log(Long userId, String telephone, String action, boolean succes,
                    String ipAddress, String details) {
        try {
            AuditLog entry = AuditLog.builder()
                    .userId(userId)
                    .telephone(telephone)
                    .action(action)
                    .statut(succes ? "SUCCESS" : "FAILURE")
                    .ipAddress(ipAddress)
                    .details(details)
                    .build();
            auditLogRepository.save(entry);
        } catch (Exception e) {
            // L'audit ne doit jamais faire échouer le flux principal
            log.error("Échec audit action={} userId={}: {}", action, userId, e.getMessage());
        }
    }
}
