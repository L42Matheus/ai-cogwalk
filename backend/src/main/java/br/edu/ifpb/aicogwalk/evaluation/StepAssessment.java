package br.edu.ifpb.aicogwalk.evaluation;

import java.util.List;

/** @param reasoning análise explícita (só na estratégia CHAIN_OF_THOUGHT; nulo nas demais) */
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
public record StepAssessment(String reasoning, List<QuestionAnswer> answers, List<ProposedAlert> alerts) {}
