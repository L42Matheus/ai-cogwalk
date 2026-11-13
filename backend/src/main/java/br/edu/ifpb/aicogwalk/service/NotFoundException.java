package br.edu.ifpb.aicogwalk.service;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String what, Object id) {
        super(what + " não encontrado(a): " + id);
    }
}
