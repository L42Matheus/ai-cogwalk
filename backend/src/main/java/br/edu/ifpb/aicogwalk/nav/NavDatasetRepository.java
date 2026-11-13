package br.edu.ifpb.aicogwalk.nav;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NavDatasetRepository extends JpaRepository<NavDataset, Long> {
    Optional<NavDataset> findByCode(String code);
}
