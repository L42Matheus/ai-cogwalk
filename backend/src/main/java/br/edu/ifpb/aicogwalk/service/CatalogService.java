package br.edu.ifpb.aicogwalk.service;

import br.edu.ifpb.aicogwalk.domain.Project;
import br.edu.ifpb.aicogwalk.domain.TaskDef;
import br.edu.ifpb.aicogwalk.navigation.NavigationPolicy;
import br.edu.ifpb.aicogwalk.repo.ProjectRepository;
import br.edu.ifpb.aicogwalk.repo.TaskDefRepository;
import br.edu.ifpb.aicogwalk.web.dto.Requests.ProjectRequest;
import br.edu.ifpb.aicogwalk.web.dto.Requests.TaskRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cadastro de projetos (sistemas avaliados) e tarefas. */
@Service
@Transactional
public class CatalogService {
    private final ProjectRepository projects;
    private final TaskDefRepository tasks;

    public CatalogService(ProjectRepository projects, TaskDefRepository tasks) {
        this.projects = projects;
        this.tasks = tasks;
    }

    public Project createProject(ProjectRequest r) {
        validateProject(r);
        return projects.save(new Project(r.name().trim(), r.baseUrl().trim(), normalizeHosts(r.allowedHosts()), r.notes()));
    }

    public Project updateProject(Long id, ProjectRequest r) {
        validateProject(r);
        Project p = projects.findById(id).orElseThrow(() -> new NotFoundException("Projeto", id));
        p.setName(r.name().trim());
        p.setBaseUrl(r.baseUrl().trim());
        p.setAllowedHosts(normalizeHosts(r.allowedHosts()));
        p.setNotes(r.notes());
        return p;
    }

    public void deleteProject(Long id) {
        projects.deleteById(id);
    }

    public TaskDef createTask(Long projectId, TaskRequest r) {
        Project p = projects.findById(projectId).orElseThrow(() -> new NotFoundException("Projeto", projectId));
        validateTask(p, r);
        TaskDef t = new TaskDef(p, r.title().trim(), r.goal().trim(), r.userProfile().trim(), r.expectedKnowledge(),
                r.startUrl().trim(), r.steps());
        return tasks.save(t);
    }

    public TaskDef updateTask(Long id, TaskRequest r) {
        TaskDef t = tasks.findById(id).orElseThrow(() -> new NotFoundException("Tarefa", id));
        validateTask(t.getProject(), r);
        t.setTitle(r.title().trim());
        t.setGoal(r.goal().trim());
        t.setUserProfile(r.userProfile().trim());
        t.setExpectedKnowledge(r.expectedKnowledge());
        t.setStartUrl(r.startUrl().trim());
        t.setSteps(r.steps());
        return t;
    }

    public void deleteTask(Long id) {
        tasks.deleteById(id);
    }

    private static void validateProject(ProjectRequest r) {
        NavigationPolicy p = new NavigationPolicy(normalizeHosts(r.allowedHosts()));
        if (!p.isAllowedUrl(r.baseUrl().trim())) {
            throw new IllegalArgumentException("A URL base precisa pertencer a um dos hosts autorizados.");
        }
    }

    private static void validateTask(Project p, TaskRequest r) {
        if (!new NavigationPolicy(p.getAllowedHosts()).isAllowedUrl(r.startUrl().trim())) {
            throw new IllegalArgumentException("A URL inicial precisa pertencer aos hosts autorizados do projeto ("
                    + p.getAllowedHosts() + ").");
        }
        r.steps().forEach(s -> {
            if (s.type() == null || s.target() == null || s.target().isBlank()) {
                throw new IllegalArgumentException("Cada ação precisa de tipo (CLICK ou LOCATE) e texto do alvo.");
            }
        });
    }

    static String normalizeHosts(String csv) {
        return String.join(",", java.util.Arrays.stream(csv.split("[,\\s]+"))
                .map(String::trim).map(String::toLowerCase).filter(h -> !h.isEmpty()).toList());
    }
}
