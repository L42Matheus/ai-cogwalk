package br.edu.ifpb.aicogwalk.nav;

import br.edu.ifpb.aicogwalk.config.JsonSupport;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Um estado de decisão: a tarefa, a tela (ou o menu em texto) e as alternativas visíveis,
 * com a contagem de escolhas humanas observadas (zero quando ainda não houve coleta).
 */
@Entity
@Table(name = "nav_state")
public class NavState {
    public static final String DEV = "DEV";
    public static final String TEST = "TEST";
    public static final String PILOT = "PILOTO";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id")
    private NavDataset dataset;
    @Column(name = "state_key", nullable = false) private String stateKey;
    @Column(nullable = false) private String variant;
    @Column(nullable = false) private String modality;
    @Column(name = "task_key", nullable = false) private String taskKey;
    @Column(name = "task_text", nullable = false, columnDefinition = "text") private String taskText;
    @Column(name = "node_path", nullable = false) private String nodePath = "";
    @Column(name = "options_json", nullable = false, columnDefinition = "text") private String optionsJson;
    @Column(name = "human_counts_json", nullable = false, columnDefinition = "text") private String humanCountsJson;
    @Column(name = "human_n", nullable = false) private int humanN;
    @Column(name = "screenshot_path") private String screenshotPath;
    private String viewport;
    @Column(nullable = false) private String split;
    @Column(name = "input_hash", nullable = false) private String inputHash;

    protected NavState() {}

    public NavState(NavDataset dataset, String stateKey, String variant, String modality, String taskKey,
                    String taskText, String nodePath, List<NavOption> options, Map<String, Integer> humanCounts,
                    String screenshotPath, String viewport, String split, String inputHash) {
        this.dataset = dataset;
        this.stateKey = stateKey;
        this.variant = variant;
        this.modality = modality;
        this.taskKey = taskKey;
        this.taskText = taskText;
        this.nodePath = nodePath == null ? "" : nodePath;
        this.optionsJson = JsonSupport.write(options);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (NavOption o : options) counts.put(o.id(), humanCounts.getOrDefault(o.id(), 0));
        this.humanCountsJson = JsonSupport.write(counts);
        this.humanN = counts.values().stream().mapToInt(Integer::intValue).sum();
        this.screenshotPath = screenshotPath;
        this.viewport = viewport;
        this.split = split;
        this.inputHash = inputHash;
    }

    public List<NavOption> getOptions() {
        return JsonSupport.read(optionsJson, new TypeReference<List<NavOption>>() {});
    }

    public Map<String, Integer> getHumanCounts() {
        return JsonSupport.read(humanCountsJson, new TypeReference<LinkedHashMap<String, Integer>>() {});
    }

    public Long getId() { return id; }
    public NavDataset getDataset() { return dataset; }
    public String getStateKey() { return stateKey; }
    public String getVariant() { return variant; }
    public String getModality() { return modality; }
    public String getTaskKey() { return taskKey; }
    public String getTaskText() { return taskText; }
    public String getNodePath() { return nodePath; }
    public int getHumanN() { return humanN; }
    public String getScreenshotPath() { return screenshotPath; }
    public String getViewport() { return viewport; }
    public String getSplit() { return split; }
    public String getInputHash() { return inputHash; }
}
