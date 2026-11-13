package br.edu.ifpb.aicogwalk.nav;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NavStateRepository extends JpaRepository<NavState, Long> {
    List<NavState> findByDatasetIdOrderByIdAsc(Long datasetId);
    long countByDatasetId(Long datasetId);
}
