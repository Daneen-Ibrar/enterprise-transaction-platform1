package com.enterprise.invoice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface PrivateMessageRepository extends JpaRepository<PrivateMessage, Long> {
    @Query("SELECT pm FROM PrivateMessage pm WHERE pm.invoice.id = :invoiceId AND ((pm.sender.id = :userId1 AND pm.recipient.id = :userId2) OR (pm.sender.id = :userId2 AND pm.recipient.id = :userId1)) ORDER BY pm.createdAt ASC")
    List<PrivateMessage> findMessagesBetweenUsers(@Param("invoiceId") Long invoiceId,
                                                  @Param("userId1") Long userId1,
                                                  @Param("userId2") Long userId2);
}