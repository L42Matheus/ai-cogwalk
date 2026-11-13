package br.edu.ifpb.aicogwalk.evaluation;

import br.edu.ifpb.aicogwalk.domain.Answer;
import br.edu.ifpb.aicogwalk.domain.Criterion;

public record QuestionAnswer(Criterion criterion, Answer answer, String justification) {}
