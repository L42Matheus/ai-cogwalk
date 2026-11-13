package br.edu.ifpb.aicogwalk.evaluation;

public record EvaluationResult(StepAssessment assessment, String systemPrompt, String userPrompt,
                               String promptSha, String rawResponse, String model,
                               long inputTokens, long outputTokens, long latencyMs, boolean cacheHit) {}
