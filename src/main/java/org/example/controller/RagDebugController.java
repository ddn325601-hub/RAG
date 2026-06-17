package org.example.controller;

import org.example.service.VectorSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class RagDebugController {

    private final VectorSearchService vectorSearchService;

    public RagDebugController(VectorSearchService vectorSearchService) {
        this.vectorSearchService = vectorSearchService;
    }

    @GetMapping("/api/rag/search")
    public ResponseEntity<FileUploadController.ApiResponse<List<VectorSearchService.SearchResult>>> search(
            @RequestParam("q") String query,
            @RequestParam(value = "topK", defaultValue = "5") int topK) {
        FileUploadController.ApiResponse<List<VectorSearchService.SearchResult>> response =
                new FileUploadController.ApiResponse<>();

        if (query == null || query.isBlank()) {
            response.setCode(400);
            response.setMessage("q cannot be blank");
            response.setData(List.of());
            return ResponseEntity.badRequest().body(response);
        }

        int safeTopK = Math.max(1, Math.min(topK, 20));
        response.setCode(200);
        response.setMessage("success");
        response.setData(vectorSearchService.searchSimilarDocuments(query, safeTopK));
        return ResponseEntity.ok(response);
    }
}
