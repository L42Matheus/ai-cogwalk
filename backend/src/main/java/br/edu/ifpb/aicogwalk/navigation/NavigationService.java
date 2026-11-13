package br.edu.ifpb.aicogwalk.navigation;

import br.edu.ifpb.aicogwalk.config.AppProperties;
import br.edu.ifpb.aicogwalk.domain.ActionType;
import br.edu.ifpb.aicogwalk.domain.TaskStep;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Executa a sequência correta de ações de uma tarefa com Playwright e captura cada tela.
 * Navegação controlada: só GET, só hosts autorizados, nenhum elemento de formulário é acionado.
 */
@Service
public class NavigationService {
    private static final Logger log = LoggerFactory.getLogger(NavigationService.class);

    static final String EXTRACT_JS = """
        () => {
          const sel = 'a[href],button,[role=button],[role=link],[role=tab],[role=menuitem],summary,input:not([type=hidden]),select,textarea';
          const vw = window.innerWidth, vh = window.innerHeight;
          document.querySelectorAll('[data-acw-id]').forEach(e => e.removeAttribute('data-acw-id'));
          const out = []; let n = 0;
          for (const el of document.querySelectorAll(sel)) {
            const r = el.getBoundingClientRect();
            const cs = getComputedStyle(el);
            if (r.width < 1 || r.height < 1 || cs.visibility === 'hidden' || cs.display === 'none' || parseFloat(cs.opacity) === 0) continue;
            let text = (el.innerText || el.value || el.getAttribute('aria-label') || el.getAttribute('title') || '').replace(/\\s+/g, ' ').trim();
            if (!text) { const img = el.querySelector('img[alt]'); if (img) text = img.alt.trim(); }
            const id = 'e' + (++n);
            el.setAttribute('data-acw-id', id);
            const top = r.top + window.scrollY, left = r.left + window.scrollX;
            out.push({ id, tag: el.tagName.toLowerCase(), role: el.getAttribute('role') || '',
              text: text.slice(0, 160), href: el.getAttribute('href') || '',
              inViewport: top < vh && top + r.height > 0 && left < vw && left + r.width > 0,
              insideForm: !!el.closest('form'),
              x: Math.round(left), y: Math.round(top), width: Math.round(r.width), height: Math.round(r.height) });
            if (out.length >= 400) break;
          }
          const headings = [...document.querySelectorAll('h1,h2,h3')]
            .filter(h => h.getBoundingClientRect().height > 0)
            .map(h => h.innerText.replace(/\\s+/g, ' ').trim()).filter(t => t).slice(0, 30);
          return { elements: out, headings };
        }
        """;

    private final AppProperties.Browser cfg;

    public NavigationService(AppProperties props) {
        this.cfg = props.browser();
    }

    /**
     * @param runDir     diretório absoluto onde salvar capturas
     * @param relPrefix  prefixo relativo ao diretório de armazenamento (gravado no banco)
     */
    public List<Capture> walk(String startUrl, List<TaskStep> steps, NavigationPolicy policy,
                              Path runDir, String relPrefix) {
        if (steps.size() > cfg.maxSteps()) {
            throw new IllegalArgumentException("A tarefa tem mais ações do que o limite (" + cfg.maxSteps() + ").");
        }
        if (!policy.isAllowedUrl(startUrl)) {
            throw new IllegalArgumentException("A URL inicial não pertence aos hosts autorizados: " + startUrl);
        }
        List<Capture> captures = new ArrayList<>();
        try {
            Files.createDirectories(runDir);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        Playwright.CreateOptions createOptions = new Playwright.CreateOptions()
                .setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1"));
        try (Playwright pw = Playwright.create(createOptions)) {
            BrowserType.LaunchOptions lo = new BrowserType.LaunchOptions().setHeadless(cfg.headless());
            if (cfg.executablePath() != null && !cfg.executablePath().isBlank()) {
                lo.setExecutablePath(Path.of(cfg.executablePath()));
            }
            try (Browser browser = pw.chromium().launch(lo)) {
                BrowserContext ctx = browser.newContext(new Browser.NewContextOptions()
                        .setViewportSize(cfg.viewportWidth(), cfg.viewportHeight())
                        .setUserAgent(cfg.userAgent())
                        .setLocale("pt-BR")
                        .setAcceptDownloads(false));
                ctx.route("**/*", route -> {
                    Request req = route.request();
                    boolean ok = req.isNavigationRequest()
                            ? policy.allowsNavigation(req.method(), req.url())
                            : "GET".equalsIgnoreCase(req.method());
                    if (ok) route.resume(); else route.abort();
                });
                Page page = ctx.newPage();
                page.setDefaultTimeout(cfg.timeoutMs());
                page.navigate(startUrl, new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(cfg.timeoutMs()));
                settle(page);

                for (int i = 0; i <= steps.size(); i++) {
                    TaskStep step = i < steps.size() ? steps.get(i) : null;
                    page.evaluate("() => window.scrollTo(0, 0)");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> raw = (Map<String, Object>) page.evaluate(EXTRACT_JS);
                    List<UiElement> elements = toElements(raw.get("elements"));
                    @SuppressWarnings("unchecked")
                    List<String> headings = (List<String>) raw.getOrDefault("headings", List.of());

                    UiElement target = null;
                    int candidates = 0;
                    if (step != null) {
                        List<UiElement> pool = step.type() == ActionType.CLICK
                                ? elements.stream().filter(policy::isClickable).toList()
                                : elements;
                        List<UiElement> cands = TextMatch.candidates(new ArrayList<>(pool), step.target());
                        candidates = cands.size();
                        if (!cands.isEmpty()) target = cands.get(0);
                    }
                    String base = "step-" + i;
                    Path shot = runDir.resolve(base + ".png");
                    Path full = runDir.resolve(base + "-full.png");
                    Path html = runDir.resolve(base + ".html");
                    page.screenshot(new Page.ScreenshotOptions().setPath(shot));
                    page.screenshot(new Page.ScreenshotOptions().setPath(full).setFullPage(true));
                    Files.writeString(html, page.content(), StandardCharsets.UTF_8);
                    captures.add(new Capture(i, page.url(), page.title(), step,
                            target == null ? null : target.id(), candidates,
                            relPrefix + "/" + base + ".png", relPrefix + "/" + base + "-full.png",
                            relPrefix + "/" + base + ".html", elements, headings, Instant.now()));

                    if (step == null) break;
                    if (target == null) {
                        throw new NavigationException("Elemento-alvo não encontrado na etapa " + (i + 1)
                                + ": \"" + step.target() + "\". Revise a sequência de ações da tarefa.", captures);
                    }
                    if (step.type() == ActionType.CLICK) {
                        Locator loc = page.locator("[data-acw-id='" + target.id() + "']");
                        loc.evaluate("el => { const a = el.closest('a'); if (a) a.removeAttribute('target'); }");
                        loc.click();
                        settle(page);
                        if (!policy.isAllowedUrl(page.url())) {
                            throw new NavigationException("A ação levou a um endereço fora dos hosts autorizados: "
                                    + page.url(), captures);
                        }
                    } else if (i + 1 < steps.size()) {
                        // LOCATE intermediário: o usuário apenas identifica o item e segue na mesma tela
                        log.debug("LOCATE na etapa {}", i);
                    }
                    Thread.sleep(cfg.stepDelayMs());
                }
                ctx.close();
            }
        } catch (NavigationException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NavigationException("Navegação interrompida.", captures);
        } catch (PlaywrightException | IOException e) {
            throw new NavigationException("Falha de navegação: " + firstLine(e.getMessage()), captures);
        }
        return captures;
    }

    private void settle(Page page) {
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(Math.min(cfg.timeoutMs(), 8000)));
        } catch (TimeoutError e) {
            // sites com requisições contínuas nunca ficam ociosos; segue com o DOM carregado
        }
    }

    @SuppressWarnings("unchecked")
    private static List<UiElement> toElements(Object raw) {
        List<UiElement> list = new ArrayList<>();
        if (!(raw instanceof List<?> items)) return list;
        for (Object o : items) {
            Map<String, Object> m = (Map<String, Object>) o;
            list.add(new UiElement(str(m.get("id")), str(m.get("tag")), str(m.get("role")), str(m.get("text")),
                    str(m.get("href")), Boolean.TRUE.equals(m.get("inViewport")), Boolean.TRUE.equals(m.get("insideForm")),
                    num(m.get("x")), num(m.get("y")), num(m.get("width")), num(m.get("height"))));
        }
        return list;
    }

    private static String str(Object o) { return o == null ? "" : o.toString(); }

    private static int num(Object o) { return o instanceof Number n ? n.intValue() : 0; }

    private static String firstLine(String s) {
        if (s == null) return "erro desconhecido";
        int i = s.indexOf('\n');
        return i < 0 ? s : s.substring(0, i);
    }
}
