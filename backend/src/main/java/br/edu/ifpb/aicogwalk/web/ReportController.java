package br.edu.ifpb.aicogwalk.web;

import br.edu.ifpb.aicogwalk.service.ReportService;
import br.edu.ifpb.aicogwalk.service.Storage;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api")
public class ReportController {
    private final ReportService reports;
    private final Storage storage;

    public ReportController(ReportService reports, Storage storage) {
        this.reports = reports;
        this.storage = storage;
    }

    @GetMapping("/runs/{id}/report.json")
    public ResponseEntity<String> json(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean download) {
        return text(reports.json(id), MediaType.APPLICATION_JSON, download ? "execucao-" + id + ".json" : null);
    }

    @GetMapping("/runs/{id}/report.md")
    public ResponseEntity<String> markdown(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean download) {
        return text(reports.markdown(id), new MediaType("text", "markdown", StandardCharsets.UTF_8),
                download ? "execucao-" + id + ".md" : null);
    }

    @GetMapping("/runs/{id}/report.html")
    public ResponseEntity<String> html(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean download) {
        return text(reports.html(id), new MediaType("text", "html", StandardCharsets.UTF_8),
                download ? "execucao-" + id + ".html" : null);
    }

    @GetMapping("/runs/{id}/export.zip")
    public ResponseEntity<byte[]> zip(@PathVariable Long id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("aicogwalk-execucao-" + id + ".zip").build().toString())
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(reports.zip(id));
    }

    /** Telas e HTML capturados (somente leitura, restritos ao diretório de armazenamento). */
    @GetMapping("/files/**")
    public ResponseEntity<Resource> file(HttpServletRequest request) {
        String rel = request.getRequestURI().substring(request.getRequestURI().indexOf("/api/files/") + "/api/files/".length());
        Path p = storage.resolve(rel);
        if (!Files.isRegularFile(p)) return ResponseEntity.notFound().build();
        MediaType type = rel.endsWith(".html") ? MediaType.TEXT_PLAIN
                : MediaTypeFactory.getMediaType(p.getFileName().toString()).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().contentType(type)
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "max-age=86400")
                .body(new FileSystemResource(p));
    }

    private static ResponseEntity<String> text(String body, MediaType type, String filename) {
        ResponseEntity.BodyBuilder b = ResponseEntity.ok().contentType(type);
        if (filename != null) {
            b.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString());
        }
        return b.body(body);
    }
}
