package br.edu.ifpb.aicogwalk.evaluation;

import java.util.Optional;

public interface CognitiveEvaluator {
    /** Nome registrado na execução (ex.: claude, offline). */
    String name();

    String modelId();

    /** Versão do prompt (ou da heurística) usada; muda sempre que o texto muda. */
    default String promptVersion() { return PromptBuilder.VERSION; }

    /** Nível de esforço do modelo, quando se aplica. */
    default String effort() { return null; }

    EvaluationResult evaluate(EvaluationInput input);

    /** Verificação semântica independente de um alerta (C3). Vazio se o avaliador não a oferece. */
    default Optional<VerificationVerdict> verify(ProposedAlert alert, EvaluationInput context) {
        return Optional.empty();
    }
}
