package br.edu.ifpb.aicogwalk.service;

import br.edu.ifpb.aicogwalk.domain.Alert;
import br.edu.ifpb.aicogwalk.domain.ReviewDecision;
import br.edu.ifpb.aicogwalk.repo.AlertRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Decisão humana sobre cada alerta, com tempo gasto (custo de revisão). */
@Service
public class ReviewService {
    private final AlertRepository alerts;

    public ReviewService(AlertRepository alerts) {
        this.alerts = alerts;
    }

    @Transactional
    public Alert review(Long alertId, ReviewDecision decision, String note, String reviewer, Integer seconds) {
        Alert a = alerts.findById(alertId).orElseThrow(() -> new NotFoundException("Alerta", alertId));
        if (decision == null) throw new IllegalArgumentException("Informe a decisão.");
        a.review(decision, note, reviewer, seconds);
        return a;
    }
}
