package br.edu.ifpb.aicogwalk.service;

import br.edu.ifpb.aicogwalk.config.AppProperties;
import br.edu.ifpb.aicogwalk.config.JsonSupport;
import br.edu.ifpb.aicogwalk.domain.AlertState;
import br.edu.ifpb.aicogwalk.domain.PromptStrategy;
import br.edu.ifpb.aicogwalk.domain.TaskStep;
import br.edu.ifpb.aicogwalk.evaluation.QuestionAnswer;
import br.edu.ifpb.aicogwalk.navigation.UiElement;
import br.edu.ifpb.aicogwalk.web.dto.Views.AlertView;
import br.edu.ifpb.aicogwalk.web.dto.Views.RunDetail;
import br.edu.ifpb.aicogwalk.web.dto.Views.RunSummary;
import br.edu.ifpb.aicogwalk.web.dto.Views.StepView;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Relatórios exportáveis de uma execução: JSON (registro completo), Markdown, HTML autocontido
 * (telas embutidas) e pacote ZIP com tudo o que é preciso para auditar e reproduzir a avaliação.
 */
@Service
public class ReportService {
    private static final String FILES = "/api/files/";

    private final QueryService query;
    private final Storage storage;
    private final AppProperties props;

    public ReportService(QueryService query, Storage storage, AppProperties props) {
        this.query = query;
        this.storage = storage;
        this.props = props;
    }

    public String json(Long runId) {
        return JsonSupport.write(query.run(runId));
    }

    public String markdown(Long runId) {
        return markdown(query.run(runId), false);
    }

    public String html(Long runId) {
        return html(query.run(runId));
    }

    public byte[] zip(Long runId) {
        RunDetail d = query.run(runId);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
            String base = "aicogwalk-execucao-" + runId + "/";
            put(zip, base + "LEIA-ME.txt", readme(d));
            put(zip, base + "relatorio.json", JsonSupport.write(d));
            put(zip, base + "relatorio.md", markdown(d, true));
            put(zip, base + "relatorio.html", html(d));
            put(zip, base + "configuracao.json", JsonSupport.write(d.config()));
            for (StepView s : d.steps()) {
                String p = base + "etapas/etapa-" + (s.index() + 1);
                copy(zip, p + "-tela.png", s.screenshotUrl());
                copy(zip, p + "-pagina-inteira.png", s.fullpageUrl());
                copy(zip, p + ".html", s.htmlUrl());
                if (s.promptUser() != null) {
                    put(zip, p + "-prompt-sistema.txt", s.promptSystem());
                    put(zip, p + "-prompt-usuario.txt", s.promptUser());
                    put(zip, p + "-resposta-bruta.json", s.rawResponse());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bos.toByteArray();
    }

    private void put(ZipOutputStream zip, String name, String text) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(Objects.requireNonNullElse(text, "").getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private void copy(ZipOutputStream zip, String name, String url) throws IOException {
        String rel = rel(url);
        if (rel == null || !storage.exists(rel)) return;
        zip.putNextEntry(new ZipEntry(name));
        zip.write(storage.read(rel));
        zip.closeEntry();
    }

    private static String rel(String url) {
        return url == null ? null : url.startsWith(FILES) ? url.substring(FILES.length()) : url;
    }

    private String readme(RunDetail d) {
        RunSummary s = d.summary();
        return """
            AI-CogWalk: pacote da execução #%d
            Tarefa: %s
            Condição: %s | Estratégia de prompt: %s | Avaliador: %s | Modelo: %s | Versão do prompt: %s

            relatorio.html       relatório legível, com as telas embutidas
            relatorio.md         o mesmo conteúdo em Markdown
            relatorio.json       registro completo (configuração, elementos, respostas, alertas, revisões)
            configuracao.json    fotografia da configuração usada
            etapas/              tela visível, página inteira, HTML, prompts e resposta bruta de cada etapa

            Para reproduzir: reavalie esta execução (mesmas capturas) com o cache em modo replay;
            a chave de cada resposta é o hash do modelo, esforço, prompts, imagem e esquema.
            """.formatted(s.id(), d.task().title(), s.condition(), s.promptStrategy(), s.evaluator(), s.model(),
                s.promptVersion());
    }

    // ---------- Markdown ----------

    String markdown(RunDetail d, boolean relativeImages) {
        RunSummary s = d.summary();
        StringBuilder md = new StringBuilder();
        md.append("# Relatório AI-CogWalk: execução #").append(s.id()).append("\n\n");
        md.append("> Os alertas são **suspeitas** de barreira geradas por IA. Só se tornam problemas após revisão humana ")
          .append("e, na pesquisa, comparação com o que iniciantes de fato enfrentaram.\n\n");
        md.append("| Campo | Valor |\n|---|---|\n");
        row(md, "Projeto", s.projectName());
        row(md, "Tarefa", d.task().title());
        row(md, "Objetivo", d.task().goal());
        row(md, "Perfil", d.task().userProfile());
        row(md, "Condição", s.condition() + " (" + conditionText(s) + ")");
        row(md, "Estratégia de prompt", String.valueOf(s.promptStrategy()));
        row(md, "Avaliador / modelo", s.evaluator() + " / " + s.model() + (s.effort() == null ? "" : " (esforço " + s.effort() + ")"));
        row(md, "Versão do prompt", s.promptVersion());
        row(md, "Status", s.status() + (s.error() == null ? "" : ": " + s.error()));
        if (s.sourceRunId() != null) row(md, "Capturas reaproveitadas de", "execução #" + s.sourceRunId());
        row(md, "Tokens (entrada / saída)", s.inputTokens() + " / " + s.outputTokens());
        row(md, "Tempo de modelo", s.llmMillis() + " ms");
        row(md, "Início / fim", s.startedAt() + " / " + s.finishedAt());
        md.append('\n');
        String warn = examplesWarning(d);
        if (warn != null) md.append("> **Atenção:** ").append(warn).append("\n\n");

        md.append("## Resumo dos alertas\n\n");
        if (d.alerts().isEmpty()) {
            md.append("Nenhum alerta gerado.\n\n");
        } else {
            md.append("| Código | Etapa | Critério | Estado | Revisão |\n|---|---|---|---|---|\n");
            for (AlertView a : d.alerts()) {
                md.append("| ").append(a.code()).append(" | ").append(a.stepIndex() + 1).append(" | ")
                  .append(a.criterion()).append(" ").append(a.criterionLabel()).append(" | ")
                  .append(stateText(a.state())).append(" | ").append(a.reviewDecision()).append(" |\n");
            }
            md.append('\n');
        }

        md.append("## Etapas\n\n");
        for (StepView st : d.steps()) {
            md.append("### Etapa ").append(st.index() + 1).append(": ").append(escMd(st.title())).append("\n\n");
            md.append("- URL: ").append(st.url()).append('\n');
            md.append("- Ação correta: ").append(action(st.action())).append('\n');
            if (st.action() != null) {
                md.append("- Elemento-alvo: ").append(st.targetElementId() == null ? "não encontrado" : st.targetElementId())
                  .append(" (").append(st.targetCandidates()).append(" candidato(s))\n");
            }
            String img = relativeImages ? "etapas/etapa-" + (st.index() + 1) + "-tela.png" : st.screenshotUrl();
            if (img != null) md.append("\n![Tela da etapa ").append(st.index() + 1).append("](").append(img).append(")\n");
            if (st.assessment() != null && st.assessment().reasoning() != null) {
                md.append("\n**Análise explícita do modelo (CoT):** ").append(escMd(st.assessment().reasoning())).append('\n');
            }
            if (st.assessment() != null) {
                md.append("\n| Pergunta | Resposta | Justificativa |\n|---|---|---|\n");
                for (QuestionAnswer q : st.assessment().answers()) {
                    md.append("| ").append(q.criterion()).append(" ").append(q.criterion().label).append(" | ")
                      .append(q.answer()).append(" | ").append(escMd(q.justification())).append(" |\n");
                }
            } else if (st.action() == null) {
                md.append("\nTela final (estado alcançado).\n");
            }
            if (st.evalError() != null) md.append("\nErro na avaliação: ").append(st.evalError()).append('\n');
            for (AlertView a : alertsOf(d, st.index())) {
                md.append("\n#### ").append(a.code()).append(" · ").append(a.criterion()).append(" ")
                  .append(a.criterionLabel()).append(" · ").append(stateText(a.state())).append("\n\n");
                md.append("- **Evidência visual (fato):** ").append(escMd(a.visualEvidence()));
                if (!a.citedElements().isEmpty()) md.append(" [").append(String.join(", ", a.citedElements())).append("]");
                md.append('\n');
                md.append("- **Diagnóstico (hipótese):** ").append(escMd(a.diagnosis())).append('\n');
                md.append("- **Recomendação (a testar):** ").append(escMd(a.recommendation())).append('\n');
                if (a.modelConfidence() != null) md.append("- Confiança declarada pelo modelo: ").append(a.modelConfidence()).append('\n');
                if (a.verificationNote() != null) md.append("- Verificação (C3): ").append(escMd(a.verificationNote())).append('\n');
                md.append("- Revisão humana: ").append(a.reviewDecision());
                if (a.reviewer() != null) md.append(" por ").append(a.reviewer());
                if (a.reviewSeconds() != null) md.append(" em ").append(a.reviewSeconds()).append(" s");
                if (a.reviewNote() != null && !a.reviewNote().isBlank()) md.append(": ").append(escMd(a.reviewNote()));
                md.append('\n');
            }
            md.append('\n');
        }
        return md.toString();
    }

    private static void row(StringBuilder md, String k, String v) {
        md.append("| ").append(k).append(" | ").append(escMd(v)).append(" |\n");
    }

    private static String escMd(String s) {
        return s == null ? "" : s.replace("|", "\\|").replace("\n", " ");
    }

    // ---------- HTML ----------

    String html(RunDetail d) {
        RunSummary s = d.summary();
        int vw = props.browser().viewportWidth(), vh = props.browser().viewportHeight();
        StringBuilder h = new StringBuilder();
        h.append("<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\">")
         .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
         .append("<title>AI-CogWalk · execução #").append(s.id()).append("</title><style>").append(CSS).append("</style></head><body><main>");
        h.append("<h1>Relatório AI-CogWalk <span class=\"muted\">execução #").append(s.id()).append("</span></h1>");
        h.append("<p class=\"note\">Os alertas são <b>suspeitas</b> de barreira geradas por IA. Só se tornam problemas após revisão humana e, na pesquisa, comparação com o que iniciantes de fato enfrentaram.</p>");
        h.append("<table class=\"meta\">");
        meta(h, "Projeto", s.projectName());
        meta(h, "Tarefa", d.task().title());
        meta(h, "Objetivo", d.task().goal());
        meta(h, "Perfil", d.task().userProfile());
        meta(h, "Condição", s.condition() + " (" + conditionText(s) + ")");
        meta(h, "Estratégia de prompt", String.valueOf(s.promptStrategy()));
        meta(h, "Avaliador / modelo", s.evaluator() + " / " + s.model() + (s.effort() == null ? "" : " (esforço " + s.effort() + ")"));
        meta(h, "Versão do prompt", s.promptVersion());
        meta(h, "Status", s.status() + (s.error() == null ? "" : ": " + s.error()));
        if (s.sourceRunId() != null) meta(h, "Capturas reaproveitadas de", "execução #" + s.sourceRunId());
        meta(h, "Tokens (entrada / saída)", s.inputTokens() + " / " + s.outputTokens());
        meta(h, "Tempo de modelo", s.llmMillis() + " ms");
        h.append("</table>");
        String warn = examplesWarning(d);
        if (warn != null) h.append("<p class=\"warn\">").append(esc(warn)).append("</p>");

        h.append("<h2>Resumo dos alertas</h2>");
        if (d.alerts().isEmpty()) h.append("<p>Nenhum alerta gerado.</p>");
        else {
            h.append("<table><tr><th>Código</th><th>Etapa</th><th>Critério</th><th>Estado</th><th>Revisão</th></tr>");
            for (AlertView a : d.alerts()) {
                h.append("<tr><td><a href=\"#").append(a.code()).append("\">").append(a.code()).append("</a></td><td>")
                 .append(a.stepIndex() + 1).append("</td><td>").append(a.criterion()).append(' ').append(esc(a.criterionLabel()))
                 .append("</td><td><span class=\"chip ").append(a.state()).append("\">").append(stateText(a.state()))
                 .append("</span></td><td>").append(a.reviewDecision()).append("</td></tr>");
            }
            h.append("</table>");
        }

        h.append("<h2>Etapas</h2>");
        for (StepView st : d.steps()) {
            h.append("<section class=\"step\"><h3>Etapa ").append(st.index() + 1).append(": ").append(esc(st.title())).append("</h3>");
            h.append("<p class=\"muted\">").append(esc(st.url())).append("</p>");
            h.append("<p><b>Ação correta:</b> ").append(esc(action(st.action()))).append("</p>");
            String rel = rel(st.screenshotUrl());
            if (rel != null && storage.exists(rel)) {
                h.append("<div class=\"shot\"><img alt=\"Tela da etapa ").append(st.index() + 1).append("\" src=\"data:image/png;base64,")
                 .append(Base64.getEncoder().encodeToString(storage.read(rel))).append("\">");
                List<String> cited = alertsOf(d, st.index()).stream().flatMap(a -> a.citedElements().stream()).toList();
                for (UiElement e : st.elements()) {
                    boolean target = e.id().equals(st.targetElementId());
                    boolean isCited = cited.contains(e.id());
                    if ((!target && !isCited) || !e.inViewport()) continue;
                    h.append("<span class=\"box ").append(target ? "target" : "cited").append("\" style=\"left:")
                     .append(pct(e.x(), vw)).append("%;top:").append(pct(e.y(), vh)).append("%;width:")
                     .append(pct(e.width(), vw)).append("%;height:").append(pct(e.height(), vh)).append("%\" title=\"")
                     .append(e.id()).append("\"></span>");
                }
                h.append("</div><p class=\"legend\"><span class=\"box-l target\"></span> elemento-alvo <span class=\"box-l cited\"></span> elemento citado em alerta</p>");
            }
            if (st.assessment() != null && st.assessment().reasoning() != null) {
                h.append("<details><summary>Análise explícita do modelo (CoT)</summary><p>").append(esc(st.assessment().reasoning())).append("</p></details>");
            }
            if (st.assessment() != null) {
                h.append("<table><tr><th>Pergunta</th><th>Resposta</th><th>Justificativa</th></tr>");
                for (QuestionAnswer q : st.assessment().answers()) {
                    h.append("<tr><td>").append(q.criterion()).append(' ').append(esc(q.criterion().label)).append("</td><td><span class=\"ans ")
                     .append(q.answer()).append("\">").append(q.answer()).append("</span></td><td>").append(esc(q.justification())).append("</td></tr>");
                }
                h.append("</table>");
            } else if (st.action() == null) {
                h.append("<p class=\"muted\">Tela final (estado alcançado).</p>");
            }
            if (st.evalError() != null) h.append("<p class=\"warn\">Erro na avaliação: ").append(esc(st.evalError())).append("</p>");
            for (AlertView a : alertsOf(d, st.index())) {
                h.append("<div class=\"alert\" id=\"").append(a.code()).append("\"><h4>").append(a.code()).append(" · ")
                 .append(a.criterion()).append(' ').append(esc(a.criterionLabel())).append(" <span class=\"chip ").append(a.state())
                 .append("\">").append(stateText(a.state())).append("</span></h4><dl>");
                dl(h, "Evidência visual (fato)", a.visualEvidence() + (a.citedElements().isEmpty() ? "" : " [" + String.join(", ", a.citedElements()) + "]"));
                dl(h, "Diagnóstico (hipótese)", a.diagnosis());
                dl(h, "Recomendação (a testar)", a.recommendation());
                if (a.modelConfidence() != null) dl(h, "Confiança declarada pelo modelo", String.valueOf(a.modelConfidence()));
                if (a.verificationNote() != null) dl(h, "Verificação (C3)", a.verificationNote());
                String rev = a.reviewDecision() + (a.reviewer() == null ? "" : " por " + a.reviewer())
                        + (a.reviewSeconds() == null ? "" : " em " + a.reviewSeconds() + " s")
                        + (a.reviewNote() == null || a.reviewNote().isBlank() ? "" : ": " + a.reviewNote());
                dl(h, "Revisão humana", rev);
                h.append("</dl></div>");
            }
            h.append("</section>");
        }
        h.append("<footer class=\"muted\">Gerado pelo AI-CogWalk. Prompts, respostas brutas e configuração completos estão no pacote ZIP desta execução.</footer>");
        h.append("</main></body></html>");
        return h.toString();
    }

    private static String pct(int v, int total) {
        return String.format(java.util.Locale.ROOT, "%.2f", Math.max(0, v) * 100.0 / total);
    }

    private static void meta(StringBuilder h, String k, String v) {
        h.append("<tr><th>").append(esc(k)).append("</th><td>").append(esc(v)).append("</td></tr>");
    }

    private static void dl(StringBuilder h, String k, String v) {
        h.append("<dt>").append(esc(k)).append("</dt><dd>").append(esc(v)).append("</dd>");
    }

    private static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s, "UTF-8");
    }

    // ---------- comuns ----------

    private static List<AlertView> alertsOf(RunDetail d, int step) {
        return d.alerts().stream().filter(a -> a.stepIndex() == step).toList();
    }

    private static String action(TaskStep step) {
        if (step == null) return "(nenhuma; tela final)";
        return switch (step.type()) {
            case CLICK -> "clicar em \"" + step.target() + "\"";
            case LOCATE -> "localizar \"" + step.target() + "\" na tela";
        } + (step.description() == null || step.description().isBlank() ? "" : " (" + step.description() + ")");
    }

    private static String stateText(AlertState s) {
        return s == AlertState.SUSPEITA ? "Suspeita" : "Não confirmada";
    }

    private static String conditionText(RunSummary s) {
        return switch (s.condition()) {
            case C1 -> "tela isolada";
            case C2 -> "com histórico da navegação";
            case C3 -> "com histórico e verificação estruturada";
        };
    }

    @SuppressWarnings("unchecked")
    private static String examplesWarning(RunDetail d) {
        if (d.summary().promptStrategy() != PromptStrategy.FEW_SHOT || d.examples() == null) return null;
        Object origins = d.examples().get("origins");
        if (origins instanceof List<?> l && l.contains("ILUSTRATIVO")) {
            return "esta execução usou casos de referência ILUSTRATIVOS (banco " + d.examples().get("bankVersion")
                    + "), não observados com participantes. Serve só para testar o pipeline; não use em resultados.";
        }
        return null;
    }

    private static final String CSS = """
        :root{--bg:#fff;--fg:#1d2330;--muted:#5d6675;--line:#d9dee7;--card:#f6f8fb;--sus:#b45309;--sus-bg:#fff4e5;--nc:#475569;--nc-bg:#eef1f5;--tgt:#2563eb;--cit:#ea580c}
        @media (prefers-color-scheme:dark){:root{--bg:#12151b;--fg:#e6e9ef;--muted:#9aa3b2;--line:#2c3340;--card:#1a1f27;--sus:#fbbf24;--sus-bg:#3a2a0c;--nc:#cbd5e1;--nc-bg:#252c37;--tgt:#60a5fa;--cit:#fb923c}}
        *{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--fg);font:15px/1.5 system-ui,-apple-system,Segoe UI,Roboto,sans-serif}
        main{max-width:980px;margin:0 auto;padding:24px 16px}h1{font-size:1.6rem}h2{margin-top:2rem;border-bottom:1px solid var(--line)}
        table{border-collapse:collapse;width:100%;margin:.6rem 0}th,td{border:1px solid var(--line);padding:6px 8px;text-align:left;vertical-align:top}
        th{background:var(--card)}.meta th{width:220px}.muted{color:var(--muted)}.note,.warn{padding:10px 12px;border-radius:8px;background:var(--card)}
        .warn{background:var(--sus-bg);color:var(--sus)}.step{margin:1.5rem 0;padding:16px;border:1px solid var(--line);border-radius:10px}
        .shot{position:relative;border:1px solid var(--line);border-radius:6px;overflow:hidden}.shot img{display:block;width:100%;height:auto}
        .box{position:absolute;border:3px solid;border-radius:3px}.box.target{border-color:var(--tgt)}.box.cited{border-color:var(--cit);border-style:dashed}
        .legend{font-size:.85rem;color:var(--muted)}.box-l{display:inline-block;width:14px;height:10px;border:2px solid;margin:0 4px 0 10px;vertical-align:middle}
        .box-l.target{border-color:var(--tgt)}.box-l.cited{border-color:var(--cit);border-style:dashed}
        .chip{font-size:.78rem;padding:2px 8px;border-radius:99px;font-weight:600}.chip.SUSPEITA{background:var(--sus-bg);color:var(--sus)}.chip.NAO_CONFIRMADA{background:var(--nc-bg);color:var(--nc)}
        .ans{font-weight:600}.ans.NAO{color:var(--sus)}.alert{margin-top:12px;padding:12px;border-left:4px solid var(--sus);background:var(--card);border-radius:6px}
        dl{margin:0}dt{font-weight:600;margin-top:6px}dd{margin:0 0 4px 0}footer{margin-top:2rem;font-size:.85rem}
        @media (max-width:600px){.meta th{width:auto}}
        """;
}
