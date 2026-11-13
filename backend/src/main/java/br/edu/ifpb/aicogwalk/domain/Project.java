package br.edu.ifpb.aicogwalk.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "project")
public class Project {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    @Column(name = "base_url", nullable = false)
    private String baseUrl;
    /** Hosts que o navegador pode visitar, separados por vírgula. */
    @Column(name = "allowed_hosts", nullable = false)
    private String allowedHosts;
    @Column(columnDefinition = "text")
    private String notes;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Project() {}

    public Project(String name, String baseUrl, String allowedHosts, String notes) {
        this.name = name;
        this.baseUrl = baseUrl;
        this.allowedHosts = allowedHosts;
        this.notes = notes;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getBaseUrl() { return baseUrl; }
    public String getAllowedHosts() { return allowedHosts; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public void setName(String name) { this.name = name; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public void setAllowedHosts(String allowedHosts) { this.allowedHosts = allowedHosts; }
    public void setNotes(String notes) { this.notes = notes; }
}
