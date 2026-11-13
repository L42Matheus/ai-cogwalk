package br.edu.ifpb.aicogwalk.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Uma execução do percurso cognitivo de uma tarefa sob uma condição experimental. */
@Entity
@Table(name = "run")
public class Run {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private TaskDef task;
    @Enumerated(EnumType.STRING) @Column(name = "exp_condition", nullable = false)
    private Condition condition;
    @Column(nullable = false)
    private String evaluator;
    private String model;
    private String effort;
    @Column(name = "prompt_version")
    private String promptVersion;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private RunStatus status = RunStatus.PENDENTE;
    @Column(columnDefinition = "text")
    private String error;
    /** Execução cujas capturas foram reaproveitadas (repetição sem nova navegação). */
    @Column(name = "source_run_id")
    private Long sourceRunId;
    /** Fotografia da configuração usada, para reprodução. */
    @Column(name = "config_json", columnDefinition = "text")
    private String configJson;
    @Enumerated(EnumType.STRING) @Column(name = "prompt_strategy", nullable = false)
    private PromptStrategy promptStrategy = PromptStrategy.ZERO_SHOT;
    /** Versão do banco de exemplos e ids dos exemplos usados (few-shot). */
    @Column(name = "examples_json", columnDefinition = "text")
    private String examplesJson;
    @Column(name = "input_tokens") private long inputTokens;
    @Column(name = "output_tokens") private long outputTokens;
    @Column(name = "llm_millis") private long llmMillis;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "finished_at") private Instant finishedAt;

    protected Run() {}

    public Run(TaskDef task, Condition condition, String evaluator, Long sourceRunId) {
        this.task = task;
        this.condition = condition;
        this.evaluator = evaluator;
        this.sourceRunId = sourceRunId;
    }

    public void addUsage(long in, long out, long millis) {
        inputTokens += in;
        outputTokens += out;
        llmMillis += millis;
    }

    public PromptStrategy getPromptStrategy() { return promptStrategy; }
    public void setPromptStrategy(PromptStrategy promptStrategy) { this.promptStrategy = promptStrategy; }
    public String getExamplesJson() { return examplesJson; }
    public void setExamplesJson(String examplesJson) { this.examplesJson = examplesJson; }
    public Long getId() { return id; }
    public TaskDef getTask() { return task; }
    public Condition getCondition() { return condition; }
    public String getEvaluator() { return evaluator; }
    public String getModel() { return model; }
    public String getEffort() { return effort; }
    public String getPromptVersion() { return promptVersion; }
    public RunStatus getStatus() { return status; }
    public String getError() { return error; }
    public Long getSourceRunId() { return sourceRunId; }
    public String getConfigJson() { return configJson; }
    public long getInputTokens() { return inputTokens; }
    public long getOutputTokens() { return outputTokens; }
    public long getLlmMillis() { return llmMillis; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setModel(String model) { this.model = model; }
    public void setEffort(String effort) { this.effort = effort; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    public void setStatus(RunStatus status) { this.status = status; }
    public void setError(String error) { this.error = error; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
