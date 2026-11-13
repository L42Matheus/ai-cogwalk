package br.edu.ifpb.aicogwalk.nav;

import br.edu.ifpb.aicogwalk.config.AppProperties;
import br.edu.ifpb.aicogwalk.config.JsonSupport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.ScreenshotAnimations;
import com.microsoft.playwright.options.ScreenshotCaret;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Renderiza localmente os protótipos HTML do estudo de Kuric et al. (2025) com Playwright,
 * no estado em que o participante decide o primeiro clique (menu principal visível).
 * Nenhum acesso à rede: só arquivos locais; requisições externas são bloqueadas.
 */
@Component
public class PrototypeRenderer {

    public record Rendered(byte[] png, List<NavOption> options, String viewport) {}

    private static final String NAV_JS = """
        () => [...document.querySelectorAll('a.nav-link.dropdown-toggle')].map(a => {
          const r = a.getBoundingClientRect();
          return { label: a.innerText.replace(/\\s+/g,' ').trim(), x: Math.round(r.left), y: Math.round(r.top),
                   width: Math.round(r.width), height: Math.round(r.height) };
        })
        """;

    private final AppProperties.Browser cfg;

    public PrototypeRenderer(AppProperties props) {
        this.cfg = props.browser();
    }

    /**
     * @param html      arquivo do protótipo (desktop.html, desktop-intrusive.html, mobile.html)
     * @param mobile    usa viewport de celular e abre o menu "hambúrguer" antes da captura
     * @param topLevel  rótulos do primeiro nível da árvore, na ordem do dataset (ids "0".."n")
     */
    /** Instante fixo do relógio simulado (data exibida nos protótipos do estudo original). */
    static final long FIXED_TIME = 1676282160000L; // 2023-02-13T09:56:00Z

    public Rendered render(Path html, boolean mobile, List<String> topLevel) {
        if (!Files.isRegularFile(html)) throw new IllegalStateException("Protótipo não encontrado: " + html);
        BrowserType.LaunchOptions lo = new BrowserType.LaunchOptions().setHeadless(true);
        if (cfg.executablePath() != null && !cfg.executablePath().isBlank()) lo.setExecutablePath(Path.of(cfg.executablePath()));
        int w = mobile ? 390 : cfg.viewportWidth();
        int h = mobile ? 844 : cfg.viewportHeight();
        try (Playwright pw = Playwright.create(new Playwright.CreateOptions().setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
             Browser browser = pw.chromium().launch(lo)) {
            Browser.NewContextOptions co = new Browser.NewContextOptions().setViewportSize(w, h).setLocale("en-GB");
            if (mobile) co.setIsMobile(true).setHasTouch(true).setDeviceScaleFactor(1);
            BrowserContext ctx = browser.newContext(co);
            ctx.route("**/*", route -> {
                String url = route.request().url();
                if (url.startsWith("file:") || url.startsWith("data:")) route.resume(); else route.abort();
            });
            Page page = ctx.newPage();
            // Relógio simulado: animações em JavaScript (o anúncio piscante do protótipo intrusivo usa
            // setInterval) avançam um número fixo de passos, então a captura é idêntica a cada importação.
            // install() mantém o tempo correndo; pauseAt() o congela, e só runFor() o avança.
            page.clock().install(new Clock.InstallOptions().setTime(FIXED_TIME));
            page.clock().pauseAt(FIXED_TIME + 1000);
            page.navigate(html.toUri().toString());
            page.clock().runFor(400);
            if (mobile) {
                Locator toggler = page.locator(".navbar-toggler");
                if (toggler.count() > 0 && toggler.first().isVisible()) {
                    toggler.first().click();
                    page.clock().runFor(600);
                    page.waitForTimeout(700); // transição CSS do menu (tempo real do navegador)
                }
            }
            Object raw = page.evaluate(NAV_JS);
            List<Map<String, Object>> found = JsonSupport.MAPPER.convertValue(raw, new TypeReference<List<Map<String, Object>>>() {});
            List<NavOption> options = new ArrayList<>();
            for (int i = 0; i < topLevel.size(); i++) {
                String label = topLevel.get(i);
                Map<String, Object> m = found.stream().filter(f -> label.equalsIgnoreCase(String.valueOf(f.get("label"))))
                        .findFirst().orElse(null);
                if (m == null) throw new IllegalStateException("Rótulo do menu não encontrado no protótipo: " + label);
                options.add(new NavOption(String.valueOf(i), label, num(m.get("x")), num(m.get("y")),
                        num(m.get("width")), num(m.get("height"))));
            }
            byte[] png = page.screenshot(new Page.ScreenshotOptions().setFullPage(false)
                    .setAnimations(ScreenshotAnimations.DISABLED).setCaret(ScreenshotCaret.HIDE));
            return new Rendered(png, options, w + "x" + h);
        }
    }

    private static Integer num(Object o) { return o == null ? null : ((Number) o).intValue(); }
}
