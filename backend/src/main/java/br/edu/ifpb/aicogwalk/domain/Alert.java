package br.edu.ifpb.aicogwalk.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Suspeita de barreira, sempre separando fato observado (evidência visual),
 * hipótese sobre o comportamento humano (diagnóstico) e recomendação.
 */
@Entity
@Table(name = "alert")
public class Alert {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id")
    private Run run;
    @Column(nullable = false) private String code;
    @Column(name = "step_index", nullable = false) private int stepIndex;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Criterion criterion;
    @Column(name = "visual_evidence", columnDefinition = "text") private String visualEvidence;
    @Column(columnDefinition = "text") private String diagnosis;
    @Column(name = "cited_elements") private String citedElements;
    @Column(columnDefinition = "text") private String recommendation;
    @Column(name = "model_confidence") private Double modelConfidence;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private AlertState state = AlertState.SUSPEITA;
    @Column(name = "verification_note", columnDefinition = "text") private String verificationNote;
    @Enumerated(EnumType.STRING) @Column(name = "review_decision", nullable = false)
    private ReviewDecision reviewDecision = ReviewDecision.PENDENTE;
    @Column(name = "review_note", columnDefinition = "text") private String reviewNote;
    private String reviewer;
    @Column(name = "review_seconds") private Integer reviewSeconds;
    @Column(name = "reviewed_at") private Instant reviewedAt;

    protected Alert() {}

    public Alert(Run run, String code, int stepIndex, Criterion criterion) {
        this.run = run;
        this.code = code;
        this.stepIndex = stepIndex;
        this.criterion = criterion;
    }

    public Long getId() { return id; }
    public Run getRun() { return run; }
    public String getCode() { return code; }
    public int getStepIndex() { return stepIndex; }
    public Criterion getCriterion() { return criterion; }
    public String getVisualEvidence() { return visualEvidence; }
    public String getDiagnosis() { return diagnosis; }
    public String getCitedElements() { return citedElements; }
    public String getRecommendation() { return recommendation; }
    public Double getModelConfidence() { return modelConfidence; }
    public AlertState getState() { return state; }
    public String getVerificationNote() { return verificationNote; }
    public ReviewDecision getReviewDecision() { return reviewDecision; }
    public String getReviewNote() { return reviewNote; }
    public String getReviewer() { return reviewer; }
    public Integer getReviewSeconds() { return reviewSeconds; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setVisualEvidence(String v) { this.visualEvidence = v; }
    public void setDiagnosis(String v) { this.diagnosis = v; }
    public void setCitedElements(String v) { this.citedElements = v; }
    public void setRecommendation(String v) { this.recommendation = v; }
    public void setModelConfidence(Double v) { this.modelConfidence = v; }
    public void setState(AlertState v) { this.state = v; }
    public void setVerificationNote(String v) { this.verificationNote = v; }
    public void review(ReviewDecision decision, String note, String reviewer, Integer seconds) {
        this.reviewDecision = decision;
        this.reviewNote = note;
        this.reviewer = reviewer;
        this.reviewSeconds = seconds;
        this.reviewedAt = Instant.now();
    }
}
