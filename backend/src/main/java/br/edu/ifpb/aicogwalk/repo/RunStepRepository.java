package br.edu.ifpb.aicogwalk.repo;

import br.edu.ifpb.aicogwalk.domain.RunStep;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RunStepRepository extends JpaRepository<RunStep, Long> {
    List<RunStep> findByRunIdOrderByStepIndexAsc(Long runId);
}
