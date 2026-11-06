package dev.jagadeesh.shortlink.api;

import dev.jagadeesh.shortlink.service.AliasTakenException;
import dev.jagadeesh.shortlink.service.InvalidUrlException;
import dev.jagadeesh.shortlink.service.LinkExpiredException;
import dev.jagadeesh.shortlink.service.LinkNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

/** Maps domain errors to RFC 7807 problem responses (application/problem+json). */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(LinkNotFoundException.class)
    ProblemDetail notFound(LinkNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Link not found", e.getMessage());
    }

    @ExceptionHandler(LinkExpiredException.class)
    ProblemDetail expired(LinkExpiredException e) {
        return problem(HttpStatus.GONE, "Link expired", e.getMessage());
    }

    @ExceptionHandler(AliasTakenException.class)
    ProblemDetail conflict(AliasTakenException e) {
        return problem(HttpStatus.CONFLICT, "Alias taken", e.getMessage());
    }

    @ExceptionHandler(InvalidUrlException.class)
    ProblemDetail badUrl(InvalidUrlException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid URL", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidBody(MethodArgumentNotValidException e) {
        Map<String, String> errors = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(f -> f.getField(), f -> String.valueOf(f.getDefaultMessage()), (a, b) -> a));
        ProblemDetail p = problem(HttpStatus.BAD_REQUEST, "Validation failed", "One or more fields are invalid");
        p.setProperty("errors", errors);
        return p;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail invalidParam(ConstraintViolationException e) {
        return problem(HttpStatus.BAD_REQUEST, "Validation failed", e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setTitle(title);
        return p;
    }
}
