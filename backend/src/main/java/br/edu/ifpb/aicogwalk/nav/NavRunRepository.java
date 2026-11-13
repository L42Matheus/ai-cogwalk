package br.edu.ifpb.aicogwalk.nav;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NavRunRepository extends JpaRepository<NavRun, Long> {
    List<NavRun> findByDatasetIdOrderByIdDesc(Long datasetId);
    List<NavRun> findAllByOrderByIdDesc();
}
