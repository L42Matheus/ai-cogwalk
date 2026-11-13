package br.edu.ifpb.aicogwalk.web.dto;

import br.edu.ifpb.aicogwalk.domain.AlertState;
import br.edu.ifpb.aicogwalk.domain.Condition;
import br.edu.ifpb.aicogwalk.domain.Criterion;
import br.edu.ifpb.aicogwalk.domain.PromptStrategy;
import br.edu.ifpb.aicogwalk.domain.ReviewDecision;
import br.edu.ifpb.aicogwalk.domain.RunStatus;
import br.edu.ifpb.aicogwalk.domain.TaskStep;
import br.edu.ifpb.aicogwalk.evaluation.StepAssessment;
import br.edu.ifpb.aicogwalk.navigation.UiElement;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Representações expostas pela API e usadas nos relatórios. */
public final class Views {
    private Views() {}

    public record ProjectView(Long id, String name, String baseUrl, String allowedHosts, String notes,
                              Instant createdAt, long taskCount) {}

    public record TaskView(Long id, Long projectId, String projectName, String title, String goal,
                           String userProfile, String expectedKnowledge, String startUrl,
                           List<TaskStep> steps, Instant createdAt) {}

    public record RunSummary(Long id, Long taskId, String taskTitle, Long projectId, String projectName,
                             Condition condition, PromptStrategy promptStrategy, String evaluator, String model,
                             String effort, String promptVersion, RunStatus status, String error, Long sourceRunId,
                             long inputTokens, long outputTokens, long llmMillis,
                             Instant createdAt, Instant startedAt, Instant finishedAt,
                             int stepCount, int alertCount, int suspectCount, int pendingReview) {}

    public record StepView(int index, String url, String title, TaskStep action, String targetElementId,
                           int targetCandidates, String screenshotUrl, String fullpageUrl, String htmlUrl,
                           List<String> headings, List<UiElement> elements, StepAssessment assessment,
                           String promptSha, String promptSystem, String promptUser, String rawResponse,
                           long inputTokens, long outputTokens, long latencyMs, boolean cacheHit,
                           String evalError) {}

    public record AlertView(Long id, String code, int stepIndex, Criterion criterion, String criterionLabel,
                            String visualEvidence, String diagnosis, List<String> citedElements,
                            String recommendation, Double modelConfidence, AlertState state,
                            String verificationNote, ReviewDecision reviewDecision, String reviewNote,
                            String reviewer, Integer reviewSeconds, Instant reviewedAt) {}

    public record RunDetail(RunSummary summary, TaskView task, Map<String, Object> config,
                            Map<String, Object> examples, List<StepView> steps, List<AlertView> alerts) {}
}
