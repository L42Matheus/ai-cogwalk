package br.edu.ifpb.aicogwalk.navigation;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Localiza o elemento-alvo de uma ação pelo texto visível. */
public final class TextMatch {
    private TextMatch() {}

    public static String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    /** Candidatos: primeiro correspondências exatas, depois por inclusão; visíveis primeiro. */
    public static List<UiElement> candidates(List<UiElement> elements, String target) {
        String t = normalize(target);
        List<UiElement> exact = new ArrayList<>();
        List<UiElement> partial = new ArrayList<>();
        for (UiElement e : elements) {
            String txt = normalize(e.text());
            if (txt.isEmpty()) continue;
            if (txt.equals(t)) exact.add(e);
            else if (txt.contains(t)) partial.add(e);
        }
        List<UiElement> chosen = exact.isEmpty() ? partial : exact;
        chosen.sort(Comparator.comparing((UiElement e) -> !e.inViewport()).thenComparing(UiElement::y));
        return chosen;
    }
}
