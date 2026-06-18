package org.example.controller;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.contest.ContestChatRequest;
import org.example.dto.contest.ContestChatResponse;
import org.example.service.ChatService;
import org.example.service.ContestSessionService;
import org.example.service.ContestVisionService;
import org.example.service.VectorSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class ContestChatController {

    private static final Logger logger = LoggerFactory.getLogger(ContestChatController.class);

    private final ChatService chatService;
    private final ContestSessionService sessionService;
    private final ContestVisionService visionService;
    private final VectorSearchService vectorSearchService;
    private final ObjectMapper objectMapper;

    @Value("${contest.api-token:change-me-contest-token}")
    private String contestApiToken;

    public ContestChatController(ChatService chatService,
                                 ContestSessionService sessionService,
                                 ContestVisionService visionService,
                                 VectorSearchService vectorSearchService,
                                 ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.sessionService = sessionService;
        this.visionService = visionService;
        this.vectorSearchService = vectorSearchService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/chat")
    public ResponseEntity<ContestChatResponse<ContestChatResponse.ChatData>> chat(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = "X-API-Token", required = false) String apiToken,
            @RequestBody ContestChatRequest request) {
        try {
            if (!isAuthorized(authorization, apiToken)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ContestChatResponse.error(401, "unauthorized"));
            }
            if (request == null || request.getQuestion() == null || request.getQuestion().isBlank()) {
                return ResponseEntity.badRequest()
                        .body(ContestChatResponse.error(400, "question cannot be blank"));
            }

            visionService.validateImages(request.getImages());

            ContestSessionService.SessionState session = sessionService.getOrCreate(request.getSessionId());
            String userQuestion = buildAgentQuestion(request.getQuestion(), request.getImages());

            DashScopeApi dashScopeApi = chatService.createDashScopeApi();
            DashScopeChatModel chatModel = chatService.createStandardChatModel(dashScopeApi);
            ReactAgent agent = chatService.createReactAgent(chatModel, buildContestSystemPrompt(session.snapshot()));
            String answer = chatService.executeChat(agent, userQuestion);
            List<ContestChatResponse.SourceData> sources = buildSources(request.getQuestion());

            session.addExchange(request.getQuestion(), answer);
            ContestChatResponse.ChatData data = new ContestChatResponse.ChatData(
                    answer,
                    session.getSessionId(),
                    System.currentTimeMillis(),
                    sources
            );
            return ResponseEntity.ok(ContestChatResponse.success(data));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ContestChatResponse.error(400, e.getMessage()));
        } catch (Exception e) {
            logger.error("contest /chat failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ContestChatResponse.error(500, "chat failed: " + e.getMessage()));
        }
    }

    private boolean isAuthorized(String authorization, String apiToken) {
        if (contestApiToken == null || contestApiToken.isBlank()) {
            return true;
        }
        return (authorization != null && authorization.equals("Bearer " + contestApiToken))
                || (apiToken != null && apiToken.equals(contestApiToken));
    }

    private List<ContestChatResponse.SourceData> buildSources(String question) {
        try {
            List<VectorSearchService.SearchResult> results =
                    vectorSearchService.searchSimilarDocuments(question, 3);
            return java.util.stream.IntStream.range(0, results.size())
                    .mapToObj(index -> {
                        VectorSearchService.SearchResult result = results.get(index);
                        SourceMetadata metadata = parseSourceMetadata(result.getMetadata(), index + 1);
                        return new ContestChatResponse.SourceData(
                                index + 1,
                                metadata.title(),
                                metadata.fileName(),
                                result.getContent(),
                                result.getScore()
                        );
                    })
                    .toList();
        } catch (Exception e) {
            logger.warn("failed to build /chat sources", e);
            return List.of();
        }
    }

    private SourceMetadata parseSourceMetadata(String metadata, int rank) {
        if (metadata == null || metadata.isBlank()) {
            return new SourceMetadata("片段 " + rank, "internal-docs");
        }
        try {
            JsonNode node = objectMapper.readTree(metadata);
            String title = textOrDefault(node, "title", "片段 " + rank);
            String fileName = textOrDefault(node, "_file_name", textOrDefault(node, "fileName", "internal-docs"));
            return new SourceMetadata(title, fileName);
        } catch (Exception e) {
            return new SourceMetadata("片段 " + rank, "internal-docs");
        }
    }

    private String textOrDefault(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        if (value == null || value.asText().isBlank()) {
            return fallback;
        }
        return value.asText();
    }

    private record SourceMetadata(String title, String fileName) {
    }

    private String buildAgentQuestion(String question, List<String> images) {
        if (images == null || images.isEmpty()) {
            return question;
        }

        String imageDescription;
        try {
            imageDescription = visionService.describeImages(question, images);
        } catch (Exception e) {
            logger.warn("vision model unavailable, fallback to text-only answer: {}", e.getMessage());
            imageDescription = "The image was received, but the configured vision model could not extract content.";
        }

        return """
                User question:
                %s

                Vision observation:
                %s

                Please answer by combining the vision observation, internal knowledge base evidence, and available tools.
                If the knowledge base does not contain enough evidence, say what is uncertain and what information is needed next.
                """.formatted(question, imageDescription.isBlank()
                ? "No clear image information was extracted."
                : imageDescription);
    }

    private String buildContestSystemPrompt(List<Map<String, String>> history) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a multimodal intelligent support agent for enterprise customer service, after-sales, and AIOps.\n");
        prompt.append("Reply in Chinese when the user writes Chinese.\n");
        prompt.append("For FAQ, policy, after-sales workflow, troubleshooting, screenshots, runbooks, alerts, logs explanation, or any request that says 'based on the internal knowledge base', call queryInternalDocs first and use those results as the primary evidence.\n");
        prompt.append("Use log and metric tools only when the user asks for live diagnosis, current incidents, concrete service status, or after internal docs have established the standard process.\n");
        prompt.append("Do not invent document names, version numbers, incidents, SQL, commands, metrics, or log facts. Only cite file names or metadata that appear in tool results. If evidence is insufficient, say so and ask for the missing order number, traceId, alert name, service name, screenshot, or time range.\n");
        prompt.append("Answer structure: conclusion first, evidence/source second, then actionable steps. Keep the answer practical for a competition demo.\n\n");

        if (!history.isEmpty()) {
            prompt.append("--- Conversation history ---\n");
            for (Map<String, String> message : history) {
                String role = "user".equals(message.get("role")) ? "User" : "Assistant";
                prompt.append(role).append(": ").append(message.get("content")).append("\n");
            }
            prompt.append("--- End history ---\n\n");
        }
        prompt.append("Now answer the user's latest question.");
        return prompt.toString();
    }
}
