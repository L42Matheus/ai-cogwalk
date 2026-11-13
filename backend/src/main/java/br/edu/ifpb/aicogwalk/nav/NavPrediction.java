package br.edu.ifpb.aicogwalk.nav;

import br.edu.ifpb.aicogwalk.config.JsonSupport;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "nav_prediction")
public class NavPrediction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id")
    private NavRun run;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "state_id")
    private NavState state;
    @Column(name = "distribution_json", nullable = false, columnDefinition = "text") private String distributionJson;
    @Column(name = "samples_json", columnDefinition = "text") private String samplesJson;
    @Column(name = "detail_json", columnDefinition = "text") private String detailJson;
    @Column(name = "prompt_system", columnDefinition = "text") private String promptSystem;
    @Column(name = "prompt_user", columnDefinition = "text") private String promptUser;
    @Column(name = "prompt_sha") private String promptSha;
    @Column(name = "metrics_json", columnDefinition = "text") private String metricsJson;
    @Column(name = "input_tokens", nullable = false) private long inputTokens;
    @Column(name = "output_tokens", nullable = false) private long outputTokens;
    @Column(name = "latency_ms", nullable = false) private long latencyMs;
    @Column(name = "cache_hits", nullable = false) private int cacheHits;
    @Column(columnDefinition = "text") private String error;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();

    protected NavPrediction() {}

    public NavPrediction(NavRun run, NavState state, Prediction p) {
        this.run = run;
        this.state = state;
        this.distributionJson = JsonSupport.write(p.distribution());
        this.samplesJson = p.samples() == null ? null : JsonSupport.write(p.samples());
        this.detailJson = p.detail() == null ? null : JsonSupport.write(p.detail());
        this.promptSystem = p.promptSystem();
        this.promptUser = p.promptUser();
        this.promptSha = p.promptSha();
        this.inputTokens = p.inputTokens();
        this.outputTokens = p.outputTokens();
        this.latencyMs = p.millis();
        this.cacheHits = p.cacheHits();
    }

    public Map<String, Double> getDistribution() {
        return JsonSupport.read(distributionJson, new TypeReference<LinkedHashMap<String, Double>>() {});
    }

    public Long getId() { return id; }
    public NavRun getRun() { return run; }
    public NavState getState() { return state; }
    public String getDistributionJson() { return distributionJson; }
    public String getSamplesJson() { return samplesJson; }
    public String getDetailJson() { return detailJson; }
    public String getPromptSystem() { return promptSystem; }
    public String getPromptUser() { return promptUser; }
    public String getPromptSha() { return promptSha; }
    public String getMetricsJson() { return metricsJson; }
    public void setMetricsJson(String metricsJson) { this.metricsJson = metricsJson; }
    public long getInputTokens() { return inputTokens; }
    public long getOutputTokens() { return outputTokens; }
    public long getLatencyMs() { return latencyMs; }
    public int getCacheHits() { return cacheHits; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public Instant getCreatedAt() { return createdAt; }
}
