package br.edu.ifpb.aicogwalk.service;

import br.edu.ifpb.aicogwalk.config.AppProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Armazenamento local das capturas (telas, HTML). Caminhos no banco são relativos a esta raiz. */
@Component
public class Storage {
    private final Path root;

    public Storage(AppProperties props) {
        this.root = Path.of(props.storageDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path root() { return root; }

    /** Resolve um caminho relativo impedindo saída da raiz. */
    public Path resolve(String relative) {
        Path p = root.resolve(relative).normalize();
        if (!p.startsWith(root)) throw new IllegalArgumentException("Caminho fora do armazenamento: " + relative);
        return p;
    }

    public byte[] read(String relative) {
        try {
            return Files.readAllBytes(resolve(relative));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public boolean exists(String relative) {
        return relative != null && Files.isRegularFile(resolve(relative));
    }
}
