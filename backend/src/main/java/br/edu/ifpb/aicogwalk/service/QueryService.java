package br.edu.ifpb.aicogwalk.service;

import br.edu.ifpb.aicogwalk.config.JsonSupport;
import br.edu.ifpb.aicogwalk.domain.Alert;
import br.edu.ifpb.aicogwalk.domain.AlertState;
import br.edu.ifpb.aicogwalk.domain.Project;
import br.edu.ifpb.aicogwalk.domain.ReviewDecision;
import br.edu.ifpb.aicogwalk.domain.Run;
import br.edu.ifpb.aicogwalk.domain.RunStep;
import br.edu.ifpb.aicogwalk.domain.TaskDef;
import br.edu.ifpb.aicogwalk.evaluation.StepAssessment;
import br.edu.ifpb.aicogwalk.navigation.Capture;
import br.edu.ifpb.aicogwalk.repo.AlertRepository;
import br.edu.ifpb.aicogwalk.repo.ProjectRepository;
import br.edu.ifpb.aicogwalk.repo.RunRepository;
import br.edu.ifpb.aicogwalk.repo.RunStepRepository;
import br.edu.ifpb.aicogwalk.repo.TaskDefRepository;
import br.edu.ifpb.aicogwalk.web.dto.Views.*;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Leituras e conversão para as representações da API. */
@Service
@Transactional(readOnly = true)
public class QueryService {
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final ProjectRepository projects;
    private final TaskDefRepository tasks;
    private final RunRepository runs;
    private final RunStepRepository steps;
    private final AlertRepository alerts;

    public QueryService(ProjectRepository projects, TaskDefRepository tasks, RunRepository runs,
                        RunStepRepository steps, AlertRepository alerts) {
        this.projects = projects;
        this.tasks = tasks;
        this.runs = runs;
        this.steps = steps;
        this.alerts = alerts;
    }

    public List<ProjectView> projects() {
        return projects.findAllByOrderByIdAsc().stream().map(this::view).toList();
    }

    public ProjectView project(Long id) {
        return view(projects.findById(id).orElseThrow(() -> new NotFoundException("Projeto", id)));
    }

    public List<TaskView> tasksOf(Long projectId) {
        return tasks.findByProjectIdOrderByIdAsc(projectId).stream().map(QueryService::view).toList();
    }

    public TaskView task(Long id) {
        return view(tasks.findById(id).orElseThrow(() -> new NotFoundException("Tarefa", id)));
    }

    public List<RunSummary> runsOfTask(Long taskId) {
        return runs.findByTaskIdOrderByIdDesc(taskId).stream().map(this::summary).toList();
    }

    public List<RunSummary> recentRuns() {
        return runs.findTop20ByOrderByIdDesc().stream().map(this::summary).toList();
    }

    public RunSummary runSummary(Long id) {
        return summary(runs.findById(id).orElseThrow(() -> new NotFoundException("Execução", id)));
    }

    public RunDetail run(Long id) {
        Run r = runs.findById(id).orElseThrow(() -> new NotFoundException("Execução", id));
        List<StepView> sv = steps.findByRunIdOrderByStepIndexAsc(id).stream().map(QueryService::view).toList();
        List<AlertView> av = alerts.findByRunIdOrderByStepIndexAscIdAsc(id).stream().map(QueryService::view).toList();
        Map<String, Object> config = r.getConfigJson() == null ? Map.of() : JsonSupport.read(r.getConfigJson(), MAP);
        Map<String, Object> examples = r.getExamplesJson() == null ? null : JsonSupport.read(r.getExamplesJson(), MAP);
        return new RunDetail(summary(r), view(r.getTask()), config, examples, sv, av);
    }

    public AlertView alert(Long id) {
        return view(alerts.findById(id).orElseThrow(() -> new NotFoundException("Alerta", id)));
    }

    private ProjectView view(Project p) {
        return new ProjectView(p.getId(), p.getName(), p.getBaseUrl(), p.getAllowedHosts(), p.getNotes(),
                p.getCreatedAt(), tasks.countByProjectId(p.getId()));
    }

    static TaskView view(TaskDef t) {
        return new TaskView(t.getId(), t.getProject().getId(), t.getProject().getName(), t.getTitle(), t.getGoal(),
                t.getUserProfile(), t.getExpectedKnowledge(), t.getStartUrl(), t.getSteps(), t.getCreatedAt());
    }

    private RunSummary summary(Run r) {
        List<Alert> al = alerts.findByRunIdOrderByStepIndexAscIdAsc(r.getId());
        int stepCount = steps.findByRunIdOrderByStepIndexAsc(r.getId()).size();
        int suspects = (int) al.stream().filter(a -> a.getState() == AlertState.SUSPEITA).count();
        int pending = (int) al.stream().filter(a -> a.getReviewDecision() == ReviewDecision.PENDENTE).count();
        TaskDef t = r.getTask();
        return new RunSummary(r.getId(), t.getId(), t.getTitle(), t.getProject().getId(), t.getProject().getName(),
                r.getCondition(), r.getPromptStrategy(), r.getEvaluator(), r.getModel(), r.getEffort(),
                r.getPromptVersion(), r.getStatus(), r.getError(), r.getSourceRunId(),
                r.getInputTokens(), r.getOutputTokens(), r.getLlmMillis(),
                r.getCreatedAt(), r.getStartedAt(), r.getFinishedAt(), stepCount, al.size(), suspects, pending);
    }

    static StepView view(RunStep st) {
        Capture c = RunWorker.toCapture(st);
        StepAssessment a = st.getAssessmentJson() == null ? null : JsonSupport.read(st.getAssessmentJson(), StepAssessment.class);
        return new StepView(st.getStepIndex(), st.getUrl(), st.getTitle(), c.action(), st.getTargetElementId(),
                st.getTargetCandidates(), fileUrl(st.getScreenshotPath()), fileUrl(st.getFullpagePath()),
                fileUrl(st.getHtmlPath()), c.headings(), c.elements(), a, st.getPromptSha(), st.getPromptSystem(),
                st.getPromptUser(), st.getRawResponse(), st.getInputTokens(), st.getOutputTokens(),
                st.getLatencyMs(), st.isCacheHit(), st.getEvalError());
    }

    static AlertView view(Alert a) {
        List<String> cited = a.getCitedElements() == null || a.getCitedElements().isBlank() ? List.of()
                : Arrays.stream(a.getCitedElements().split(",")).map(String::trim).toList();
        return new AlertView(a.getId(), a.getCode(), a.getStepIndex(), a.getCriterion(), a.getCriterion().label,
                a.getVisualEvidence(), a.getDiagnosis(), cited, a.getRecommendation(), a.getModelConfidence(),
                a.getState(), a.getVerificationNote(), a.getReviewDecision(), a.getReviewNote(), a.getReviewer(),
                a.getReviewSeconds(), a.getReviewedAt());
    }

    static String fileUrl(String rel) {
        return rel == null ? null : "/api/files/" + rel;
    }
}
