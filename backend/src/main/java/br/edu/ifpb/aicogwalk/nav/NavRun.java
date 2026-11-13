package br.edu.ifpb.aicogwalk.nav;

import br.edu.ifpb.aicogwalk.domain.RunStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "nav_run")
public class NavRun {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id")
    private NavDataset dataset;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private NavMethod method;
    private String model;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private RunStatus status = RunStatus.PENDENTE;
    @Column(columnDefinition = "text") private String error;
    @Column(name = "config_json", columnDefinition = "text") private String configJson;
    @Column(name = "summary_json", columnDefinition = "text") private String summaryJson;
    @Column(name = "frozen_hash") private String frozenHash;
    @Column(name = "states_total", nullable = false) private int statesTotal;
    @Column(name = "states_done", nullable = false) private int statesDone;
    @Column(name = "input_tokens", nullable = false) private long inputTokens;
    @Column(name = "output_tokens", nullable = false) private long outputTokens;
    @Column(name = "llm_calls", nullable = false) private int llmCalls;
    @Column(name = "llm_millis", nullable = false) private long llmMillis;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "finished_at") private Instant finishedAt;

    protected NavRun() {}

    public NavRun(NavDataset dataset, NavMethod method, String model) {
        this.dataset = dataset;
        this.method = method;
        this.model = model;
    }

    public void addUsage(long in, long out, int calls, long millis) {
        inputTokens += in;
        outputTokens += out;
        llmCalls += calls;
        llmMillis += millis;
    }

    public Long getId() { return id; }
    public NavDataset getDataset() { return dataset; }
    public NavMethod getMethod() { return method; }
    public String getModel() { return model; }
    public RunStatus getStatus() { return status; }
    public void setStatus(RunStatus status) { this.status = status; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }
    public String getSummaryJson() { return summaryJson; }
    public void setSummaryJson(String summaryJson) { this.summaryJson = summaryJson; }
    public String getFrozenHash() { return frozenHash; }
    public void setFrozenHash(String frozenHash) { this.frozenHash = frozenHash; }
    public int getStatesTotal() { return statesTotal; }
    public void setStatesTotal(int statesTotal) { this.statesTotal = statesTotal; }
    public int getStatesDone() { return statesDone; }
    public void setStatesDone(int statesDone) { this.statesDone = statesDone; }
    public long getInputTokens() { return inputTokens; }
    public long getOutputTokens() { return outputTokens; }
    public int getLlmCalls() { return llmCalls; }
    public long getLlmMillis() { return llmMillis; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
