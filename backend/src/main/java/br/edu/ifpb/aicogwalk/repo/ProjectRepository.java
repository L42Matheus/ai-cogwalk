package br.edu.ifpb.aicogwalk.repo;

import br.edu.ifpb.aicogwalk.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    java.util.List<Project> findAllByOrderByIdAsc();
}
