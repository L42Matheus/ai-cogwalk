package br.edu.ifpb.aicogwalk.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Uma tela capturada e, quando avaliada, o resultado das quatro perguntas.
 * A tela de índice i é a tela em que o usuário precisa executar a ação i;
 * a última tela (índice = número de ações) é o estado final.
 */
@Entity
@Table(name = "run_step")
public class RunStep {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id")
    private Run run;
    @Column(name = "step_index", nullable = false) private int stepIndex;
    @Column(columnDefinition = "text") private String url;
    @Column(columnDefinition = "text") private String title;
    @Column(name = "action_json", columnDefinition = "text") private String actionJson;
    @Column(name = "target_element_id") private String targetElementId;
    @Column(name = "target_candidates") private int targetCandidates;
    @Column(name = "screenshot_path") private String screenshotPath;
    @Column(name = "fullpage_path") private String fullpagePath;
    @Column(name = "html_path") private String htmlPath;
    @Column(name = "elements_json", columnDefinition = "text") private String elementsJson;
    @Column(name = "headings_json", columnDefinition = "text") private String headingsJson;
    @Column(name = "captured_at") private Instant capturedAt;
    // avaliação
    @Column(name = "assessment_json", columnDefinition = "text") private String assessmentJson;
    @Column(name = "prompt_system", columnDefinition = "text") private String promptSystem;
    @Column(name = "prompt_user", columnDefinition = "text") private String promptUser;
    @Column(name = "prompt_sha") private String promptSha;
    @Column(name = "raw_response", columnDefinition = "text") private String rawResponse;
    @Column(name = "input_tokens") private long inputTokens;
    @Column(name = "output_tokens") private long outputTokens;
    @Column(name = "latency_ms") private long latencyMs;
    @Column(name = "cache_hit") private boolean cacheHit;
    @Column(name = "eval_error", columnDefinition = "text") private String evalError;

    protected RunStep() {}

    public RunStep(Run run, int stepIndex) {
        this.run = run;
        this.stepIndex = stepIndex;
    }

    public Long getId() { return id; }
    public Run getRun() { return run; }
    public int getStepIndex() { return stepIndex; }
    public String getUrl() { return url; }
    public String getTitle() { return title; }
    public String getActionJson() { return actionJson; }
    public String getTargetElementId() { return targetElementId; }
    public int getTargetCandidates() { return targetCandidates; }
    public String getScreenshotPath() { return screenshotPath; }
    public String getFullpagePath() { return fullpagePath; }
    public String getHtmlPath() { return htmlPath; }
    public String getElementsJson() { return elementsJson; }
    public String getHeadingsJson() { return headingsJson; }
    public Instant getCapturedAt() { return capturedAt; }
    public String getAssessmentJson() { return assessmentJson; }
    public String getPromptSystem() { return promptSystem; }
    public String getPromptUser() { return promptUser; }
    public String getPromptSha() { return promptSha; }
    public String getRawResponse() { return rawResponse; }
    public long getInputTokens() { return inputTokens; }
    public long getOutputTokens() { return outputTokens; }
    public long getLatencyMs() { return latencyMs; }
    public boolean isCacheHit() { return cacheHit; }
    public String getEvalError() { return evalError; }
    public void setUrl(String url) { this.url = url; }
    public void setTitle(String title) { this.title = title; }
    public void setActionJson(String actionJson) { this.actionJson = actionJson; }
    public void setTargetElementId(String targetElementId) { this.targetElementId = targetElementId; }
    public void setTargetCandidates(int targetCandidates) { this.targetCandidates = targetCandidates; }
    public void setScreenshotPath(String screenshotPath) { this.screenshotPath = screenshotPath; }
    public void setFullpagePath(String fullpagePath) { this.fullpagePath = fullpagePath; }
    public void setHtmlPath(String htmlPath) { this.htmlPath = htmlPath; }
    public void setElementsJson(String elementsJson) { this.elementsJson = elementsJson; }
    public void setHeadingsJson(String headingsJson) { this.headingsJson = headingsJson; }
    public void setCapturedAt(Instant capturedAt) { this.capturedAt = capturedAt; }
    public void setAssessmentJson(String assessmentJson) { this.assessmentJson = assessmentJson; }
    public void setPromptSystem(String promptSystem) { this.promptSystem = promptSystem; }
    public void setPromptUser(String promptUser) { this.promptUser = promptUser; }
    public void setPromptSha(String promptSha) { this.promptSha = promptSha; }
    public void setRawResponse(String rawResponse) { this.rawResponse = rawResponse; }
    public void setInputTokens(long inputTokens) { this.inputTokens = inputTokens; }
    public void setOutputTokens(long outputTokens) { this.outputTokens = outputTokens; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }
    public void setCacheHit(boolean cacheHit) { this.cacheHit = cacheHit; }
    public void setEvalError(String evalError) { this.evalError = evalError; }
}
