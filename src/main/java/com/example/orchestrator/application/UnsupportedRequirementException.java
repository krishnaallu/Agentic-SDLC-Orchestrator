package com.example.orchestrator.application;

public class UnsupportedRequirementException extends RuntimeException {
    public UnsupportedRequirementException(String message) {
        super(message);
    }
}