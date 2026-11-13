package br.edu.ifpb.aicogwalk.evaluation;

import br.edu.ifpb.aicogwalk.domain.Condition;
import br.edu.ifpb.aicogwalk.navigation.Capture;
import br.edu.ifpb.aicogwalk.domain.PromptStrategy;
import java.util.List;

/**
 * Entrada da avaliação de uma ação.
 *
 * @param current  tela em que a ação precisa ser executada
 * @param after    tela resultante (para Q4)
 * @param history  telas e ações anteriores (vazio em C1)
 * @param examples casos de referência de outros sistemas (vazio em zero-shot)
 */
public record EvaluationInput(String goal, String userProfile, String expectedKnowledge,
                              Condition condition, int stepIndex, int totalSteps,
                              Capture current, Capture after, List<Capture> history,
                              byte[] screenshotPng, int maxElements,
                              List<FewShotExample> examples, PromptStrategy strategy) {}
