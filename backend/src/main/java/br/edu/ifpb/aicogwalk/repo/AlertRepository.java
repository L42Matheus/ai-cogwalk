package br.edu.ifpb.aicogwalk.repo;

import br.edu.ifpb.aicogwalk.domain.Alert;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findByRunIdOrderByStepIndexAscIdAsc(Long runId);
}
