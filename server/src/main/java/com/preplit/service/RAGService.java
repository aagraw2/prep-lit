package com.preplit.service;

import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class RAGService {

    public List<String> retrieve(String query, int topK) {
        // RAG disabled - returning empty results
        return Collections.emptyList();
    }

    public String buildContext(String query, int topK) {
        // RAG disabled - returning empty context
        return "";
    }
}
