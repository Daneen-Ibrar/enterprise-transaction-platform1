package com.enterprise.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface UserActivityLogRepository extends JpaRepository<UserActivityLog, Long>,
        JpaSpecificationExecutor<UserActivityLog> {

    Page<UserActivityLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<UserActivityLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<UserActivityLog> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);

    @Query("SELECT DISTINCT l.action FROM UserActivityLog l ORDER BY l.action")
    List<String> findDistinctActions();
}