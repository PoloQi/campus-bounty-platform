package com.campus.bounty.admin;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAuditService {
    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long adminId, String action, String targetType, Long targetId, String details) {
        entityManager.persist(new AdminLog(adminId, action, targetType, targetId, details));
    }
}
