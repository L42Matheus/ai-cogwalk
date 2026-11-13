package br.edu.ifpb.aicogwalk.nav;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NavPredictionRepository extends JpaRepository<NavPrediction, Long> {
    List<NavPrediction> findByRunIdOrderByIdAsc(Long runId);
}
