package br.edu.ifpb.aicogwalk.evaluation;

import br.edu.ifpb.aicogwalk.domain.Criterion;

/**
 * Caso de referência para few-shot.
 *
 * @param sourceHost       host do sistema de origem; usado para excluir casos do sistema avaliado
 * @param origin           OBSERVADO (vindo de sessões com participantes) ou ILUSTRATIVO (só para testar o pipeline)
 * @param problem          true se houve barreira; false para casos sem problema (evita viés para alertar)
 * @param observedBehavior o que os participantes fizeram (ou, se ILUSTRATIVO, o que se descreve como hipótese)
 */
public record FewShotExample(String id, String sourceSystem, String sourceHost, String origin,
                             String taskGoal, String action, Criterion criterion, boolean problem,
                             String screenEvidence, String observedBehavior, String lesson) {}
