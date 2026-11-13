package br.edu.ifpb.aicogwalk.repo;

import br.edu.ifpb.aicogwalk.domain.Run;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RunRepository extends JpaRepository<Run, Long> {
    java.util.List<Run> findByTaskIdOrderByIdDesc(Long taskId);
    java.util.List<Run> findByTaskProjectIdOrderByIdDesc(Long projectId);
    java.util.List<Run> findTop20ByOrderByIdDesc();
}
