package br.edu.ifpb.aicogwalk.nav;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Alternativa de navegação visível num estado (link, item de menu). Coordenadas opcionais, em pixels da captura. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NavOption(String id, String label, Integer x, Integer y, Integer width, Integer height) {
    public NavOption(String id, String label) { this(id, label, null, null, null, null); }
}
