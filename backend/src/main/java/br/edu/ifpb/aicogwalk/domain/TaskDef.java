package br.edu.ifpb.aicogwalk.domain;

import br.edu.ifpb.aicogwalk.config.JsonSupport;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "task_def")
public class TaskDef {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;
    @Column(nullable = false)
    private String title;
    @Column(nullable = false, columnDefinition = "text")
    private String goal;
    @Column(name = "user_profile", nullable = false, columnDefinition = "text")
    private String userProfile;
    @Column(name = "expected_knowledge", columnDefinition = "text")
    private String expectedKnowledge;
    @Column(name = "start_url", nullable = false)
    private String startUrl;
    @Column(name = "steps_json", nullable = false, columnDefinition = "text")
    private String stepsJson;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected TaskDef() {}

    public TaskDef(Project project, String title, String goal, String userProfile,
                   String expectedKnowledge, String startUrl, List<TaskStep> steps) {
        this.project = project;
        this.title = title;
        this.goal = goal;
        this.userProfile = userProfile;
        this.expectedKnowledge = expectedKnowledge;
        this.startUrl = startUrl;
        setSteps(steps);
    }

    public List<TaskStep> getSteps() {
        return JsonSupport.read(stepsJson, new TypeReference<List<TaskStep>>() {});
    }

    public void setSteps(List<TaskStep> steps) { this.stepsJson = JsonSupport.write(steps); }

    public Long getId() { return id; }
    public Project getProject() { return project; }
    public String getTitle() { return title; }
    public String getGoal() { return goal; }
    public String getUserProfile() { return userProfile; }
    public String getExpectedKnowledge() { return expectedKnowledge; }
    public String getStartUrl() { return startUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public void setTitle(String title) { this.title = title; }
    public void setGoal(String goal) { this.goal = goal; }
    public void setUserProfile(String userProfile) { this.userProfile = userProfile; }
    public void setExpectedKnowledge(String expectedKnowledge) { this.expectedKnowledge = expectedKnowledge; }
    public void setStartUrl(String startUrl) { this.startUrl = startUrl; }
}
