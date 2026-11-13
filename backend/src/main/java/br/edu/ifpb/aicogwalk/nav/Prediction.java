package br.edu.ifpb.aicogwalk.nav;

import java.util.List;
import java.util.Map;

/**
 * Saída de um preditor para um estado.
 *
 * @param distribution probabilidade prevista por id de opção (soma 1)
 * @param samples      escolhas individuais, quando o método simula participantes
 * @param detail       dados auxiliares (similaridades, avaliações Q1–Q3, justificativas)
 */
public record Prediction(Map<String, Double> distribution, List<String> samples, Object detail,
                         String promptSystem, String promptUser, String promptSha,
                         long inputTokens, long outputTokens, long millis, int cacheHits) {

    public static Prediction of(Map<String, Double> distribution, Object detail) {
        return new Prediction(distribution, null, detail, null, null, null, 0, 0, 0, 0);
    }
}
