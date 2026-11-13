package br.edu.ifpb.aicogwalk.nav;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** API do módulo AI-CogWalk-NAV (predição de escolhas de navegação). */
@RestController
@RequestMapping("/api/nav")
public class NavController {
    private final NavService service;
    private final NavQuery query;

    public NavController(NavService service, NavQuery query) {
        this.service = service;
        this.query = query;
    }

    public record StartRunRequest(NavMethod method, String model, String effort, Integer samples,
                                  List<String> variants, List<String> splits, Double tau,
                                  List<String> stateKeys) {}

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("importStatus", service.importStatus());
        m.put("methods", Arrays.stream(NavMethod.values()).map(x -> Map.of("id", x.name(), "llm", x.llm,
                "sampled", x.sampled, "cognitive", x.cognitive, "perOption", x.perOption, "description", x.description)).toList());
        m.put("anthropicConfigured", NavLlmClient.configured("claude"));
        m.put("openaiConfigured", NavLlmClient.configured("gpt-4.1"));
        m.put("promptVersion", NavPrompts.VERSION);
        return m;
    }

    @GetMapping("/datasets")
    public List<Map<String, Object>> datasets() { return query.datasets(); }

    @GetMapping("/datasets/{id}")
    public Map<String, Object> dataset(@PathVariable Long id) { return query.dataset(id); }

    @PostMapping("/datasets/kuric2025")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> importKuric() {
        service.importKuricAsync();
        return Map.of("importStatus", service.importStatus());
    }

    /** Captura independente da página inicial de uma tarefa (sem seguir a rota correta). */
    @PostMapping("/tasks/{taskId}/capture")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> capture(@PathVariable Long taskId) {
        NavState s = service.captureTask(taskId);
        return Map.of("stateId", s.getId(), "datasetId", s.getDataset().getId(), "datasetCode", "tarefa-" + taskId);
    }

    @PostMapping("/datasets/{id}/runs")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> start(@PathVariable Long id, @RequestBody StartRunRequest req) {
        if (req.method() == null) throw new IllegalArgumentException("Informe o método (B0, B1, B1_CW, B1_R, B1_S, B1_CW_S, UNIFORM).");
        NavRun r = service.start(id, req.method(), req.model(), req.effort(), req.samples(), req.variants(), req.splits(), req.tau(), req.stateKeys());
        return Map.of("id", r.getId());
    }

    @GetMapping("/runs")
    public List<Map<String, Object>> runs() { return query.runs(); }

    @GetMapping("/runs/{id}")
    public Map<String, Object> run(@PathVariable Long id) { return query.runDetail(id); }

    @GetMapping("/predictions/{id}")
    public Map<String, Object> prediction(@PathVariable Long id) { return query.prediction(id); }

    @GetMapping("/runs/{id}/export.json")
    public ResponseEntity<Map<String, Object>> exportJson(@PathVariable Long id) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename("nav-rodada-" + id + ".json").build().toString()).body(query.export(id));
    }

    @GetMapping(value = "/runs/{id}/export.csv")
    public ResponseEntity<String> exportCsv(@PathVariable Long id) {
        return ResponseEntity.ok().contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("nav-rodada-" + id + ".csv").build().toString())
                .body(query.exportCsv(id));
    }
}
