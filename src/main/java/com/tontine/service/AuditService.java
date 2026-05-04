package com.tontine.service;

public interface AuditService {

    void log(Long userId, String telephone, String action, boolean succes, String details);

    void log(Long userId, String telephone, String action, boolean succes,
             String ipAddress, String details);
}
