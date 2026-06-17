package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class ContestVisionService {

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    @Value("${contest.vision.model:qwen3.7-plus}")
    private String visionModel;

    @Value("${contest.vision.endpoint:https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions}")
    private String visionEndpoint;

    @Value("${contest.vision.max-images:3}")
    private int maxImages;

    @Value("${contest.vision.max-image-bytes:5242880}")
    private int maxImageBytes;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public void validateImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return;
        }
        if (images.size() > maxImages) {
            throw new IllegalArgumentException("images最多支持" + maxImages + "张");
        }
        for (String image : images) {
            ImagePayload payload = parseImagePayload(image);
            if (payload.bytes() > maxImageBytes) {
                throw new IllegalArgumentException("单张图片不能超过" + (maxImageBytes / 1024 / 1024) + "MB");
            }
        }
    }

    public String describeImages(String question, List<String> images) throws Exception {
        if (images == null || images.isEmpty()) {
            return "";
        }

        List<Map<String, Object>> userContent = new ArrayList<>();
        for (String image : images) {
            userContent.add(Map.of(
                    "type", "image_url",
                    "image_url", Map.of("url", toDataUrl(image))
            ));
        }
        userContent.add(Map.of(
                "type", "text",
                "text", "用户问题：" + question + "\n请提取图片中的关键信息、可见文字、异常现象和可能与问题相关的线索。"
        ));

        Map<String, Object> requestBody = Map.of(
                "model", visionModel,
                "messages", List.of(
                        Map.of(
                                "role", "system",
                                "content", "你是企业客服与技术支持场景的图片理解助手。请只描述图片中与用户问题、故障排查、产品界面、告警截图、日志截图相关的信息。"
                        ),
                        Map.of(
                                "role", "user",
                                "content", userContent
                        )
                ),
                "temperature", 0.1
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(visionEndpoint))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("vision model request failed, status=" + response.statusCode() + ", body=" + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
        if (contentNode.isMissingNode() || contentNode.isNull()) {
            return "";
        }
        return contentNode.asText("").trim();
    }

    private String toDataUrl(String image) {
        if (image == null) {
            throw new IllegalArgumentException("images中不能包含空图片");
        }
        String trimmed = image.trim();
        if (trimmed.startsWith("data:image/")) {
            return trimmed;
        }
        byte[] decoded = decodeImage(trimmed);
        return "data:" + detectMimeType(decoded) + ";base64," + trimmed;
    }

    private ImagePayload parseImagePayload(String image) {
        String data = toDataUrl(image);
        int commaIndex = data.indexOf(',');
        if (commaIndex < 0 || commaIndex == data.length() - 1) {
            throw new IllegalArgumentException("图片必须是Base64字符串或data:image/...;base64格式");
        }
        byte[] decoded = decodeImage(data.substring(commaIndex + 1));
        return new ImagePayload(decoded.length);
    }

    private byte[] decodeImage(String base64) {
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("图片Base64内容不合法");
        }
    }

    private String detectMimeType(byte[] bytes) {
        if (bytes.length >= 8
                && (bytes[0] & 0xff) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4e
                && bytes[3] == 0x47) {
            return "image/png";
        }
        if (bytes.length >= 3
                && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        if (bytes.length >= 6
                && bytes[0] == 0x47
                && bytes[1] == 0x49
                && bytes[2] == 0x46) {
            return "image/gif";
        }
        return "image/jpeg";
    }

    private record ImagePayload(int bytes) {
    }
}
