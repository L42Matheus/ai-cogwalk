package br.edu.ifpb.aicogwalk.web;

import br.edu.ifpb.aicogwalk.config.AppProperties;
import br.edu.ifpb.aicogwalk.domain.Condition;
import br.edu.ifpb.aicogwalk.domain.Criterion;
import br.edu.ifpb.aicogwalk.domain.PromptStrategy;
import br.edu.ifpb.aicogwalk.evaluation.EvaluatorFactory;
import br.edu.ifpb.aicogwalk.evaluation.ExampleBank;
import br.edu.ifpb.aicogwalk.service.CatalogService;
import br.edu.ifpb.aicogwalk.service.QueryService;
import br.edu.ifpb.aicogwalk.service.ReviewService;
import br.edu.ifpb.aicogwalk.service.RunService;
import br.edu.ifpb.aicogwalk.web.dto.Requests.*;
import br.edu.ifpb.aicogwalk.web.dto.Views.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final CatalogService catalog;
    private final QueryService query;
    private final RunService runs;
    private final ReviewService review;
    private final EvaluatorFactory evaluators;
    private final ExampleBank bank;
    private final AppProperties props;

    public ApiController(CatalogService catalog, QueryService query, RunService runs, ReviewService review,
                         EvaluatorFactory evaluators, ExampleBank bank, AppProperties props) {
        this.catalog = catalog;
        this.query = query;
        this.runs = runs;
        this.review = review;
        this.evaluators = evaluators;
        this.bank = bank;
        this.props = props;
    }

    /** Estado do serviço, usado pelo painel para montar os formulários. */
    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("defaultEvaluator", evaluators.defaultName());
        m.put("claudeConfigured", evaluators.claudeConfigured());
        m.put("model", props.llm().model());
        m.put("effort", props.llm().effort());
        m.put("cacheMode", props.llm().cacheMode());
        m.put("conditions", Arrays.stream(Condition.values()).map(c -> Map.of(
                "id", c.name(), "history", c.history, "verification", c.verification)).toList());
        m.put("strategies", Arrays.asList(PromptStrategy.values()));
        m.put("criteria", Arrays.stream(Criterion.values()).map(c -> Map.of(
                "id", c.name(), "label", c.label, "question", c.question)).toList());
        m.put("exampleBank", Map.of("version", bank.version(), "size", bank.all().size(),
                "illustrative", bank.all().stream().anyMatch(e -> "ILUSTRATIVO".equals(e.origin()))));
        m.put("demoPortal", props.demoPortal().enabled());
        return m;
    }

    // ---- projetos ----
    @GetMapping("/projects")
    public List<ProjectView> projects() { return query.projects(); }

    @GetMapping("/projects/{id}")
    public ProjectView project(@PathVariable Long id) { return query.project(id); }

    @PostMapping("/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectView createProject(@Valid @RequestBody ProjectRequest r) {
        return query.project(catalog.createProject(r).getId());
    }

    @PutMapping("/projects/{id}")
    public ProjectView updateProject(@PathVariable Long id, @Valid @RequestBody ProjectRequest r) {
        catalog.updateProject(id, r);
        return query.project(id);
    }

    @DeleteMapping("/projects/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProject(@PathVariable Long id) { catalog.deleteProject(id); }

    // ---- tarefas ----
    @GetMapping("/projects/{id}/tasks")
    public List<TaskView> tasks(@PathVariable Long id) { return query.tasksOf(id); }

    @PostMapping("/projects/{id}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskView createTask(@PathVariable Long id, @Valid @RequestBody TaskRequest r) {
        return query.task(catalog.createTask(id, r).getId());
    }

    @GetMapping("/tasks/{id}")
    public TaskView task(@PathVariable Long id) { return query.task(id); }

    @PutMapping("/tasks/{id}")
    public TaskView updateTask(@PathVariable Long id, @Valid @RequestBody TaskRequest r) {
        catalog.updateTask(id, r);
        return query.task(id);
    }

    @DeleteMapping("/tasks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTask(@PathVariable Long id) { catalog.deleteTask(id); }

    // ---- execuções ----
    @GetMapping("/runs")
    public List<RunSummary> recentRuns() { return query.recentRuns(); }

    @GetMapping("/tasks/{id}/runs")
    public List<RunSummary> runsOfTask(@PathVariable Long id) { return query.runsOfTask(id); }

    @PostMapping("/tasks/{id}/runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RunSummary startRun(@PathVariable Long id, @RequestBody(required = false) RunRequest r) {
        RunRequest req = r == null ? new RunRequest(null, null, null) : r;
        return query.runSummary(runs.start(id, req.condition(), req.promptStrategy(), req.evaluator()).getId());
    }

    @PostMapping("/runs/{id}/reevaluate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RunSummary reevaluate(@PathVariable Long id, @RequestBody(required = false) RunRequest r) {
        RunRequest req = r == null ? new RunRequest(null, null, null) : r;
        return query.runSummary(runs.reevaluate(id, req.condition(), req.promptStrategy(), req.evaluator()).getId());
    }

    @GetMapping("/runs/{id}")
    public RunDetail run(@PathVariable Long id) { return query.run(id); }

    @GetMapping("/runs/{id}/summary")
    public RunSummary runSummary(@PathVariable Long id) { return query.runSummary(id); }

    // ---- revisão ----
    @PostMapping("/alerts/{id}/review")
    public AlertView reviewAlert(@PathVariable Long id, @Valid @RequestBody ReviewRequest r) {
        review.review(id, r.decision(), r.note(), r.reviewer(), r.seconds());
        return query.alert(id);
    }
}
