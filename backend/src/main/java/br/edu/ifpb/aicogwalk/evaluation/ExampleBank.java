package br.edu.ifpb.aicogwalk.evaluation;

import br.edu.ifpb.aicogwalk.config.JsonSupport;
import br.edu.ifpb.aicogwalk.navigation.NavigationPolicy;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Banco versionado de casos de referência para few-shot.
 * A seleção é determinística (ordem do arquivo) e exclui casos cujo host pertence
 * aos hosts autorizados do projeto avaliado, para não vazar a resposta.
 */
@Component
public class ExampleBank {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BankFile(String version, String note, List<FewShotExample> examples) {}

    public record Selection(String bankVersion, List<FewShotExample> examples, List<String> excludedIds) {
        public List<String> ids() { return examples.stream().map(FewShotExample::id).toList(); }
    }

    private final BankFile bank;
    private final int k;

    public ExampleBank(@Value("${aicogwalk.few-shot.bank:classpath:prompt-examples/exemplos.json}") String location,
                       @Value("${aicogwalk.few-shot.k:3}") int k) {
        Resource r = new DefaultResourceLoader().getResource(location);
        try (InputStream in = r.getInputStream()) {
            this.bank = JsonSupport.MAPPER.readValue(in, BankFile.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Banco de exemplos não encontrado: " + location, e);
        }
        this.k = k;
    }

    public String version() { return bank.version(); }

    public List<FewShotExample> all() { return bank.examples(); }

    public Selection select(String allowedHostsCsv) {
        NavigationPolicy policy = new NavigationPolicy(allowedHostsCsv);
        List<FewShotExample> chosen = new ArrayList<>();
        List<String> excluded = new ArrayList<>();
        for (FewShotExample ex : bank.examples()) {
            if (ex.sourceHost() != null && policy.isAllowedUrl("https://" + ex.sourceHost() + "/")) {
                excluded.add(ex.id());
                continue;
            }
            if (chosen.size() < k) chosen.add(ex);
        }
        return new Selection(bank.version(), List.copyOf(chosen), List.copyOf(excluded));
    }
}
