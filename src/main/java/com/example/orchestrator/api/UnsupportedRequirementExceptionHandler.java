package com.example.orchestrator.api;

import com.example.orchestrator.application.UnsupportedRequirementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class UnsupportedRequirementExceptionHandler {
    @ExceptionHandler(UnsupportedRequirementException.class)
    public ResponseEntity<ProblemDetail> handleUnsupportedRequirement(UnsupportedRequirementException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
                exception.getMessage());
        problem.setTitle("Unsupported generated-service domain");
        return ResponseEntity.unprocessableEntity().body(problem);
    }
}