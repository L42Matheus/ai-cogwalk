package br.edu.ifpb.aicogwalk.evaluation;

/**
 * @param evidenceHolds  a evidência visual descrita existe na tela
 * @param consequential  a evidência implica dificuldade plausível para iniciantes (não é só um fato neutro)
 */
public record VerificationVerdict(boolean evidenceHolds, boolean consequential, String reason,
                                  long inputTokens, long outputTokens, long latencyMs) {}
