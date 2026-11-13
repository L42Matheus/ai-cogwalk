package br.edu.ifpb.aicogwalk.navigation;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Regras de navegação controlada: só hosts autorizados, só requisições GET,
 * nunca acionar elementos de formulário.
 */
public final class NavigationPolicy {
    private final List<String> allowedHosts;

    public NavigationPolicy(String allowedHostsCsv) {
        this.allowedHosts = Arrays.stream(allowedHostsCsv.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT)).toList();
        if (allowedHosts.isEmpty()) {
            throw new IllegalArgumentException("Informe ao menos um host autorizado.");
        }
    }

    public boolean isAllowedUrl(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equals("http") || scheme.equals("https"))) {
                return false;
            }
            String host = uri.getHost();
            if (host == null) return false;
            String h = host.toLowerCase(Locale.ROOT);
            return allowedHosts.stream().anyMatch(a -> h.equals(a) || h.endsWith("." + a));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Requisição de navegação de documento que pode seguir. */
    public boolean allowsNavigation(String method, String url) {
        return "GET".equalsIgnoreCase(method) && isAllowedUrl(url);
    }

    /** Elemento que pode ser clicado: link ou botão fora de formulário. */
    public boolean isClickable(UiElement el) {
        if (el.insideForm()) return false;
        String tag = el.tag();
        String role = el.role() == null ? "" : el.role();
        return tag.equals("a") || tag.equals("button") || tag.equals("summary")
                || role.equals("link") || role.equals("button") || role.equals("tab") || role.equals("menuitem");
    }

    public List<String> allowedHosts() { return allowedHosts; }
}
