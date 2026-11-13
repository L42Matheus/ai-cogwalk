package br.edu.ifpb.aicogwalk.navigation;

import br.edu.ifpb.aicogwalk.domain.TaskStep;
import java.time.Instant;
import java.util.List;

/**
 * Uma tela capturada. {@code action} é a ação que o usuário precisa executar nesta tela
 * (nulo na tela final).
 */
public record Capture(int index, String url, String title, TaskStep action,
                      String targetElementId, int targetCandidates,
                      String screenshotPath, String fullpagePath, String htmlPath,
                      List<UiElement> elements, List<String> headings, Instant capturedAt) {}
