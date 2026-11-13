package br.edu.ifpb.aicogwalk.evaluation;

import br.edu.ifpb.aicogwalk.domain.Criterion;
import java.util.List;

/**
 * @param visualEvidence  fato observado na tela (o que está lá)
 * @param diagnosis       hipótese sobre a dificuldade do usuário iniciante
 * @param citedElementIds ids dos elementos da captura que sustentam a evidência
 */
public record ProposedAlert(Criterion criterion, String visualEvidence, String diagnosis,
                            List<String> citedElementIds, String recommendation, Double confidence) {}
