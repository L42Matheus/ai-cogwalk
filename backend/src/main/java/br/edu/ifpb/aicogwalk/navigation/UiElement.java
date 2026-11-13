package br.edu.ifpb.aicogwalk.navigation;

/**
 * Elemento interativo visível capturado da tela.
 *
 * @param id         identificador estável na captura (e1, e2, ...), citado pelos alertas
 * @param inViewport visível sem rolar a página no tamanho de tela usado
 * @param insideForm o elemento pertence a um formulário (o navegador nunca o aciona)
 */
public record UiElement(String id, String tag, String role, String text, String href,
                        boolean inViewport, boolean insideForm, int x, int y, int width, int height) {}
