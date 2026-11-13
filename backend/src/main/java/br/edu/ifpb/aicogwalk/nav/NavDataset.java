package br.edu.ifpb.aicogwalk.nav;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "nav_dataset")
public class NavDataset {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true) private String code;
    @Column(nullable = false) private String name;
    @Column(name = "source_url") private String sourceUrl;
    private String revision;
    private String license;
    @Column(columnDefinition = "text") private String notes;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();

    protected NavDataset() {}

    public NavDataset(String code, String name, String sourceUrl, String revision, String license, String notes) {
        this.code = code;
        this.name = name;
        this.sourceUrl = sourceUrl;
        this.revision = revision;
        this.license = license;
        this.notes = notes;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getSourceUrl() { return sourceUrl; }
    public String getRevision() { return revision; }
    public String getLicense() { return license; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
}
