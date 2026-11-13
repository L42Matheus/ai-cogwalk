package br.edu.ifpb.aicogwalk;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Marco 1 de ponta a ponta, com o avaliador offline e o portal fictício servido pelo próprio backend:
 * navegar, avaliar, visualizar telas, revisar, reproduzir a avaliação e exportar o relatório.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EndToEndTest {
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    @LocalServerPort int port;
    @Autowired TestRestTemplate http;

    @SuppressWarnings("unchecked")
    @Test
    void walksEvaluatesReviewsReproducesAndExports() throws Exception {
        String base = "http://localhost:" + port + "/portal-exemplo/";
        Map<String, Object> project = post("/api/projects", Map.of("name", "Portal fictício", "baseUrl", base,
                "allowedHosts", "localhost"));
        Number pid = (Number) project.get("id");

        Map<String, Object> task = post("/api/projects/" + pid + "/tasks", Map.of(
                "title", "Localizar o Edital 01/2026", "goal", "Encontrar o Edital nº 01/2026 do Banco de Especialistas",
                "userProfile", "Estudante iniciante", "startUrl", base + "index.html",
                "steps", List.of(
                        Map.of("type", "CLICK", "target", "Núcleo de Inovação"),
                        Map.of("type", "CLICK", "target", "Editais"),
                        Map.of("type", "CLICK", "target", "Editais de 2026"),
                        Map.of("type", "LOCATE", "target", "Edital nº 01/2026 – Banco de Especialistas"))));
        Number tid = (Number) task.get("id");

        // URL fora dos hosts autorizados é recusada no cadastro
        ResponseEntity<String> bad = http.postForEntity("/api/projects/" + pid + "/tasks", Map.of(
                "title", "x", "goal", "x", "userProfile", "x", "startUrl", "https://example.org/",
                "steps", List.of(Map.of("type", "CLICK", "target", "x"))), String.class);
        assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        Map<String, Object> run = post("/api/tasks/" + tid + "/runs", Map.of("condition", "C3", "evaluator", "offline"));
        Number rid = (Number) run.get("id");
        Map<String, Object> detail = waitFor(rid);
        Map<String, Object> summary = (Map<String, Object>) detail.get("summary");
        assertThat(summary.get("status")).as(String.valueOf(summary.get("error"))).isEqualTo("CONCLUIDA");
        assertThat(summary.get("promptStrategy")).isEqualTo("ZERO_SHOT");

        List<Map<String, Object>> steps = (List<Map<String, Object>>) detail.get("steps");
        assertThat(steps).hasSize(5);
        assertThat(steps.get(4).get("title").toString()).contains("Editais de 2026");
        for (Map<String, Object> s : steps.subList(0, 4)) {
            assertThat(s.get("assessment")).isNotNull();
            assertThat(s.get("promptSha")).isNotNull();
            assertThat(s.get("targetElementId")).isNotNull();
        }
        // a tela capturada é servida
        ResponseEntity<byte[]> png = http.getForEntity(steps.get(0).get("screenshotUrl").toString(), byte[].class);
        assertThat(png.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(png.getBody()).hasSizeGreaterThan(1000);
        // caminho fora do armazenamento é bloqueado
        assertThat(http.getForEntity("/api/files/../build.gradle", String.class).getStatusCode().is2xxSuccessful()).isFalse();

        List<Map<String, Object>> alerts = (List<Map<String, Object>>) detail.get("alerts");
        assertThat(alerts).isNotEmpty();
        assertThat(alerts).allSatisfy(a -> assertThat(a.get("verificationNote")).isNotNull());
        Number aid = (Number) alerts.get(0).get("id");
        Map<String, Object> reviewed = post("/api/alerts/" + aid + "/review",
                Map.of("decision", "PROCEDENTE", "reviewer", "teste", "seconds", 42, "note", "confere"));
        assertThat(reviewed.get("reviewDecision")).isEqualTo("PROCEDENTE");

        // reprodução: mesmas capturas, mesma avaliação
        Map<String, Object> again = post("/api/runs/" + rid + "/reevaluate", Map.of());
        Map<String, Object> d2 = waitFor((Number) again.get("id"));
        assertThat(((Map<String, Object>) d2.get("summary")).get("sourceRunId")).isEqualTo(rid.intValue());
        List<Map<String, Object>> steps2 = (List<Map<String, Object>>) d2.get("steps");
        for (int i = 0; i < 4; i++) {
            assertThat(steps2.get(i).get("promptSha")).isEqualTo(steps.get(i).get("promptSha"));
            assertThat(steps2.get(i).get("assessment")).isEqualTo(steps.get(i).get("assessment"));
        }
        assertThat(((List<?>) d2.get("alerts")).size()).isEqualTo(alerts.size());

        // few-shot registra os exemplos usados
        Map<String, Object> fs = post("/api/runs/" + rid + "/reevaluate", Map.of("promptStrategy", "FEW_SHOT"));
        Map<String, Object> d3 = waitFor((Number) fs.get("id"));
        assertThat(((Map<String, Object>) d3.get("examples")).get("ids")).isEqualTo(List.of("IL-01", "IL-02", "IL-03"));

        // relatórios
        String html = http.getForObject("/api/runs/" + rid + "/report.html", String.class);
        assertThat(html).contains("data:image/png;base64,").contains("PROCEDENTE").contains("Evidência visual (fato)");
        String md = http.getForObject("/api/runs/" + rid + "/report.md", String.class);
        assertThat(md).contains("## Resumo dos alertas").contains("Diagnóstico (hipótese)");
        String fsMd = http.getForObject("/api/runs/" + fs.get("id") + "/report.md", String.class);
        assertThat(fsMd).contains("ILUSTRATIVOS");
        byte[] zip = http.getForObject("/api/runs/" + rid + "/export.zip", byte[].class);
        List<String> names = new ArrayList<>();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e; (e = z.getNextEntry()) != null; ) names.add(e.getName());
        }
        String root = "aicogwalk-execucao-" + rid + "/";
        assertThat(names).contains(root + "relatorio.html", root + "relatorio.json", root + "configuracao.json",
                root + "etapas/etapa-1-tela.png", root + "etapas/etapa-1-prompt-usuario.txt",
                root + "etapas/etapa-1-resposta-bruta.json");
    }

    @SuppressWarnings("unchecked")
    @Test
    void navigationFailureKeepsPartialCaptures() throws Exception {
        String base = "http://localhost:" + port + "/portal-exemplo/";
        Number pid = (Number) post("/api/projects", Map.of("name", "P2", "baseUrl", base, "allowedHosts", "localhost")).get("id");
        Number tid = (Number) post("/api/projects/" + pid + "/tasks", Map.of("title", "t", "goal", "g",
                "userProfile", "u", "startUrl", base + "index.html",
                "steps", List.of(Map.of("type", "CLICK", "target", "Rótulo que não existe")))).get("id");
        Number rid = (Number) post("/api/tasks/" + tid + "/runs", Map.of("condition", "C2", "evaluator", "offline")).get("id");
        Map<String, Object> d = waitFor(rid);
        Map<String, Object> s = (Map<String, Object>) d.get("summary");
        assertThat(s.get("status")).isEqualTo("FALHOU");
        assertThat(s.get("error").toString()).contains("Rótulo que não existe");
        assertThat((List<?>) d.get("steps")).hasSize(1);
    }

    private Map<String, Object> post(String url, Object body) {
        ResponseEntity<Map<String, Object>> r = http.exchange(url, HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(body), MAP);
        assertThat(r.getStatusCode().is2xxSuccessful()).as(url + " -> " + r.getBody()).isTrue();
        return r.getBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> waitFor(Number runId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 120_000;
        while (System.currentTimeMillis() < deadline) {
            Map<String, Object> d = http.getForObject("/api/runs/" + runId, Map.class);
            Object st = ((Map<String, Object>) d.get("summary")).get("status");
            if ("CONCLUIDA".equals(st) || "FALHOU".equals(st)) return d;
            Thread.sleep(300);
        }
        throw new AssertionError("Execução " + runId + " não terminou a tempo");
    }
}
