package br.edu.ifpb.aicogwalk.nav;

import br.edu.ifpb.aicogwalk.config.AppProperties;
import br.edu.ifpb.aicogwalk.evaluation.Hashing;
import br.edu.ifpb.aicogwalk.service.Storage;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Baixa, confere e extrai os artefatos externos (dataset de Kuric et al. e modelo de embeddings). */
@Component
public class NavAssets {
    private static final Logger log = LoggerFactory.getLogger(NavAssets.class);

    private final AppProperties.Nav cfg;
    private final Storage storage;
    private volatile MiniLmEmbedder embedder;
    private volatile String embedderSha;

    public NavAssets(AppProperties props, Storage storage) {
        this.cfg = props.nav();
        this.storage = storage;
    }

    /** Diretório extraído do repositório de Kuric et al. (2025) no commit fixado. */
    public Path kuricDir() {
        if (cfg.kuricLocalDir() != null && !cfg.kuricLocalDir().isBlank()) {
            Path p = Path.of(cfg.kuricLocalDir());
            if (!Files.isRegularFile(p.resolve("analysis/data/results.csv"))) {
                throw new IllegalStateException("Diretório local do dataset sem analysis/data/results.csv: " + p);
            }
            return p;
        }
        Path dir = storage.resolve("datasets/kuric2025/" + cfg.kuricRevision());
        if (Files.isRegularFile(dir.resolve("analysis/data/results.csv"))) return dir;
        Path zip = storage.resolve("datasets/kuric2025/" + cfg.kuricRevision() + ".zip");
        download(cfg.kuricArchiveUrl(), zip);
        unzipStripFirst(zip, dir);
        return dir;
    }

    public synchronized MiniLmEmbedder embedder() {
        if (embedder != null) return embedder;
        Path dir = storage.resolve("models/all-MiniLM-L6-v2");
        if (!Files.isRegularFile(dir.resolve("model.onnx"))) {
            Path tgz = storage.resolve("models/all-MiniLM-L6-v2.tar.gz");
            download(cfg.embeddingsUrl(), tgz);
            String sha = sha256File(tgz);
            if (cfg.embeddingsSha256() != null && !cfg.embeddingsSha256().isBlank() && !cfg.embeddingsSha256().equals(sha)) {
                throw new IllegalStateException("Hash do modelo de embeddings não confere: " + sha);
            }
            untarGzFlat(tgz, dir);
        }
        embedderSha = sha256File(dir.resolve("model.onnx"));
        embedder = new MiniLmEmbedder(dir);
        return embedder;
    }

    public String embedderSha() {
        embedder();
        return embedderSha;
    }

    private void download(String url, Path target) {
        if (Files.isRegularFile(target)) return;
        try {
            Files.createDirectories(target.getParent());
            log.info("Baixando {}", url);
            HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(30)).build();
            HttpResponse<Path> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(10))
                    .header("User-Agent", "AI-CogWalk/0.2 (pesquisa academica)").GET().build(),
                    HttpResponse.BodyHandlers.ofFile(target.resolveSibling(target.getFileName() + ".part")));
            if (r.statusCode() != 200) {
                Files.deleteIfExists(r.body());
                throw new IllegalStateException("Download falhou (HTTP " + r.statusCode() + "): " + url);
            }
            Files.move(r.body(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Download falhou: " + url + " (" + e.getMessage() + ")", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** Extrai o .zip do GitHub removendo o diretório raiz "repo-sha/". */
    static void unzipStripFirst(Path zip, Path dir) {
        try (ZipInputStream z = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zip)))) {
            ZipEntry e;
            Path root = dir.toAbsolutePath().normalize();
            while ((e = z.getNextEntry()) != null) {
                String name = e.getName();
                int slash = name.indexOf('/');
                if (slash < 0 || slash == name.length() - 1) continue;
                String rel = name.substring(slash + 1);
                if (!(rel.startsWith("analysis/data/") || rel.startsWith("webpage/") || rel.equals("README.md"))) continue;
                Path out = root.resolve(rel).normalize();
                if (!out.startsWith(root)) throw new IllegalStateException("Entrada inválida no zip: " + name);
                if (e.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(z, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Não foi possível extrair " + zip, ex);
        }
    }

    /** Extrai um .tar.gz simples (arquivos regulares) achatando os diretórios. */
    static void untarGzFlat(Path tgz, Path dir) {
        try (InputStream in = new GZIPInputStream(new BufferedInputStream(Files.newInputStream(tgz)))) {
            Files.createDirectories(dir);
            byte[] header = new byte[512];
            while (true) {
                if (in.readNBytes(header, 0, 512) < 512) break;
                String name = new String(header, 0, 100, java.nio.charset.StandardCharsets.US_ASCII).trim().replace("\0", "");
                if (name.isEmpty()) break;
                String sizeOct = new String(header, 124, 12, java.nio.charset.StandardCharsets.US_ASCII).replace("\0", "").trim();
                long size = sizeOct.isEmpty() ? 0 : Long.parseLong(sizeOct, 8);
                char type = (char) header[156];
                String base = Path.of(name).getFileName().toString();
                if ((type == '0' || type == 0) && !base.startsWith("._") && size > 0) {
                    Path out = dir.resolve(base);
                    try (var os = Files.newOutputStream(out)) {
                        long left = size;
                        byte[] buf = new byte[65536];
                        while (left > 0) {
                            int n = in.read(buf, 0, (int) Math.min(buf.length, left));
                            if (n < 0) throw new IOException("tar truncado");
                            os.write(buf, 0, n);
                            left -= n;
                        }
                    }
                } else {
                    in.skipNBytes(size);
                }
                long pad = (512 - (size % 512)) % 512;
                in.skipNBytes(pad);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Não foi possível extrair " + tgz, ex);
        }
    }

    static String sha256File(Path p) {
        try (InputStream in = Files.newInputStream(p)) {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            return java.util.HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String hash(String... parts) { return Hashing.sha256(parts); }
}
