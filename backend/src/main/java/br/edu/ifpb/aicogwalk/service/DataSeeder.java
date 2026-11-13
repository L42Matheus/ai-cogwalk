package br.edu.ifpb.aicogwalk.service;

import br.edu.ifpb.aicogwalk.config.AppProperties;
import br.edu.ifpb.aicogwalk.domain.ActionType;
import br.edu.ifpb.aicogwalk.domain.Project;
import br.edu.ifpb.aicogwalk.domain.TaskDef;
import br.edu.ifpb.aicogwalk.domain.TaskStep;
import br.edu.ifpb.aicogwalk.repo.ProjectRepository;
import br.edu.ifpb.aicogwalk.repo.TaskDefRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Cadastra, num banco vazio, o portal fictício de testes e o caso do IFPB.
 * O portal fictício é servido pelo próprio backend (/portal-exemplo), então o navegador
 * acessa localhost: funciona igual no Docker Compose e no Railway.
 */
@Component
public class DataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    static final String PROFILE = "Estudante iniciante: nunca acessou a área de editais do portal e não conhece "
            + "a estrutura administrativa da instituição.";

    private final ProjectRepository projects;
    private final TaskDefRepository tasks;
    private final AppProperties props;
    private final int port;

    public DataSeeder(ProjectRepository projects, TaskDefRepository tasks, AppProperties props,
                      @Value("${server.port:8080}") int port) {
        this.projects = projects;
        this.tasks = tasks;
        this.props = props;
        this.port = port;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.seed() || projects.count() > 0) return;
        if (props.demoPortal().enabled()) {
            String base = "http://localhost:" + port + "/portal-exemplo/";
            Project demo = projects.save(new Project("Portal fictício (testes)", base, "localhost",
                    "Portal controlado pela equipe, servido pelo próprio backend. Use-o para testar o pipeline "
                            + "antes de avaliar sites reais."));
            tasks.save(new TaskDef(demo, "Localizar o Edital nº 01/2026 do Banco de Especialistas",
                    "Encontrar o Edital nº 01/2026 de credenciamento do Banco de Especialistas do Núcleo de Inovação.",
                    PROFILE, "Sabe que o edital existe, mas não sabe em que seção ele foi publicado.",
                    base + "index.html", List.of(
                    new TaskStep(ActionType.CLICK, "Núcleo de Inovação", "menu principal"),
                    new TaskStep(ActionType.CLICK, "Editais", "menu do Núcleo"),
                    new TaskStep(ActionType.CLICK, "Editais de 2026", "arquivo por ano"),
                    new TaskStep(ActionType.LOCATE, "Edital nº 01/2026 – Banco de Especialistas", "item na lista do ano"))));
        }
        Project ifpb = projects.save(new Project("Portal do IFPB – Polo de Inovação", "https://www.ifpb.edu.br/polodeinovacao",
                "ifpb.edu.br",
                "Caso piloto. Navegação apenas em páginas públicas, só GET, sem formulários, sem cadastro e com intervalo "
                        + "entre ações. Os rótulos das ações precisam ser conferidos no portal antes da primeira execução."));
        tasks.save(new TaskDef(ifpb, "Localizar o Edital nº 01/2026 do Banco de Especialistas",
                "Localizar o Edital nº 01/2026 do Banco de Especialistas do Polo de Inovação.",
                PROFILE, "Sabe que o edital existe, mas não sabe em que seção ele foi publicado.",
                "https://www.ifpb.edu.br/polodeinovacao", List.of(
                new TaskStep(ActionType.CLICK, "Editais", "menu do Polo de Inovação"),
                new TaskStep(ActionType.CLICK, "Editais de 2026", "pasta do ano"),
                new TaskStep(ActionType.LOCATE, "Banco de Especialistas", "edital 01/2026 na lista"))));
        log.info("Dados iniciais cadastrados.");
    }
}
