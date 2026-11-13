package br.edu.ifpb.aicogwalk.domain;

/** As quatro perguntas do percurso cognitivo (Wharton et al., 1994). */
public enum Criterion {
    Q1("Objetivo correto", "O usuário tentará atingir o efeito que esta etapa exige?"),
    Q2("Ação visível", "O usuário perceberá que a ação correta está disponível?"),
    Q3("Associação entre ação e objetivo", "O usuário entenderá que essa ação leva ao que deseja?"),
    Q4("Progresso percebido", "Depois de agir, o usuário perceberá que avançou?");

    public final String label;
    public final String question;

    Criterion(String label, String question) {
        this.label = label;
        this.question = question;
    }
}
