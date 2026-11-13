package br.edu.ifpb.aicogwalk.navigation;

import java.util.List;

/** Falha de navegação; carrega as telas capturadas até o ponto da falha. */
public class NavigationException extends RuntimeException {
    private final transient List<Capture> partial;

    public NavigationException(String message, List<Capture> partial) {
        super(message);
        this.partial = List.copyOf(partial);
    }

    public List<Capture> getPartial() { return partial; }
}
