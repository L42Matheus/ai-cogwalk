package br.edu.ifpb.aicogwalk.repo;

import br.edu.ifpb.aicogwalk.domain.TaskDef;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskDefRepository extends JpaRepository<TaskDef, Long> {
    java.util.List<TaskDef> findByProjectIdOrderByIdAsc(Long projectId);
    long countByProjectId(Long projectId);
}
