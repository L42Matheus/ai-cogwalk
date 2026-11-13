package br.edu.ifpb.aicogwalk.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Uma ação da sequência correta da tarefa.
 *
 * @param type        tipo de ação
 * @param target      texto visível do link/botão (CLICK) ou do item procurado (LOCATE)
 * @param description descrição da ação para o avaliador, em linguagem natural
 */
public record TaskStep(@NotNull ActionType type, @NotBlank String target, String description) {}
