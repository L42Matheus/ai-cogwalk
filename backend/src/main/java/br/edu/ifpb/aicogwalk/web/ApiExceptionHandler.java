package br.edu.ifpb.aicogwalk.web;

import br.edu.ifpb.aicogwalk.service.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(NotFoundException e) {
        return body(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> badRequest(Exception e) {
        String msg = e instanceof HttpMessageNotReadableException ? "Requisição inválida (JSON malformado ou valor desconhecido)." : e.getMessage();
        return body(HttpStatus.BAD_REQUEST, msg);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage()).collect(Collectors.joining("; "));
        return body(HttpStatus.BAD_REQUEST, msg);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflict(IllegalStateException e) {
        return body(HttpStatus.CONFLICT, e.getMessage());
    }

    private static ResponseEntity<Map<String, String>> body(HttpStatus s, String msg) {
        return ResponseEntity.status(s).body(Map.of("error", msg == null ? s.getReasonPhrase() : msg));
    }
}
