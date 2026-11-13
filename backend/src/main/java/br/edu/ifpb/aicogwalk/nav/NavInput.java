package br.edu.ifpb.aicogwalk.nav;

import java.util.List;

/**
 * Entrada do preditor: apenas o que o participante tem no momento da decisão.
 * Não existe campo para ação correta, alvo, candidatos nem dados humanos.
 */
public record NavInput(String taskText, List<NavOption> options, byte[] screenshotPng, String viewport) {
    public static NavInput of(NavState s, byte[] png) {
        return new NavInput(s.getTaskText(), s.getOptions(), png, s.getViewport());
    }
}
