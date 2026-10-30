
package com.finiq;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AuditRunRepository
        extends JpaRepository<AuditRun, Long> {

    List<AuditRun> findAllByOrderByCreatedAtDesc();
}
