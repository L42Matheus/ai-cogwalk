package br.edu.ifpb.aicogwalk.web.dto;

import br.edu.ifpb.aicogwalk.domain.Condition;
import br.edu.ifpb.aicogwalk.domain.PromptStrategy;
import br.edu.ifpb.aicogwalk.domain.ReviewDecision;
import br.edu.ifpb.aicogwalk.domain.TaskStep;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public final class Requests {
    private Requests() {}

    public record ProjectRequest(@NotBlank String name, @NotBlank String baseUrl, @NotBlank String allowedHosts,
                                 String notes) {}

    public record TaskRequest(@NotBlank String title, @NotBlank String goal, @NotBlank String userProfile,
                              String expectedKnowledge, @NotBlank String startUrl,
                              @NotEmpty List<@Valid @NotNull TaskStep> steps) {}

    public record RunRequest(Condition condition, PromptStrategy promptStrategy, String evaluator) {}

    public record ReviewRequest(@NotNull ReviewDecision decision, String note, String reviewer, Integer seconds) {}
}
