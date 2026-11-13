package br.edu.ifpb.aicogwalk.domain;

/**
 * Condições experimentais. Cada uma difere da anterior por um único fator.
 * <ul>
 *   <li>C1: Q1–Q4 sobre a tela isolada, sem histórico.</li>
 *   <li>C2: Q1–Q4 com o histórico da navegação (baseline forte).</li>
 *   <li>C3: C2 + verificação estruturada das evidências de cada alerta.
 *       Evidências humanas de calibração entram numa etapa posterior.</li>
 * </ul>
 */
public enum Condition {
    C1(false, false),
    C2(true, false),
    C3(true, true);

    public final boolean history;
    public final boolean verification;

    Condition(boolean history, boolean verification) {
        this.history = history;
        this.verification = verification;
    }
}
