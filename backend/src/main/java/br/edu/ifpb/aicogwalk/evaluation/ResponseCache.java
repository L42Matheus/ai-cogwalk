package br.edu.ifpb.aicogwalk.evaluation;

import br.edu.ifpb.aicogwalk.config.JsonSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Cache de respostas do modelo em arquivos, para reproduzir uma avaliação byte a byte.
 * Modos: {@code off}, {@code read-write}, {@code replay} (falha se a resposta não estiver no cache).
 */
public class ResponseCache {

    public record Entry(String raw, long inputTokens, long outputTokens) {}

    private final Path dir;
    private final String mode;

    public ResponseCache(Path dir, String mode) {
        this.dir = dir;
        this.mode = mode == null ? "off" : mode;
    }

    public boolean enabled() { return !mode.equals("off"); }

    public boolean replayOnly() { return mode.equals("replay"); }

    public Optional<Entry> get(String key) {
        if (!enabled()) return Optional.empty();
        Path f = dir.resolve(key + ".json");
        if (!Files.exists(f)) return Optional.empty();
        try {
            return Optional.of(JsonSupport.read(Files.readString(f, StandardCharsets.UTF_8), Entry.class));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public void put(String key, Entry entry) {
        if (!enabled()) return;
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(key + ".json"), JsonSupport.write(entry), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível gravar o cache de respostas.", e);
        }
    }
}
