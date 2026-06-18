package org.example.dto.contest;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ContestChatResponse<T> {

    private int code;

    private String msg;

    private T data;

    public static <T> ContestChatResponse<T> success(T data) {
        ContestChatResponse<T> response = new ContestChatResponse<>();
        response.setCode(0);
        response.setMsg("success");
        response.setData(data);
        return response;
    }

    public static <T> ContestChatResponse<T> error(int code, String msg) {
        ContestChatResponse<T> response = new ContestChatResponse<>();
        response.setCode(code);
        response.setMsg(msg);
        return response;
    }

    @Getter
    @Setter
    public static class ChatData {
        private String answer;

        @JsonProperty("session_id")
        private String sessionId;

        private long timestamp;

        private List<SourceData> sources;

        public ChatData(String answer, String sessionId, long timestamp) {
            this(answer, sessionId, timestamp, List.of());
        }

        public ChatData(String answer, String sessionId, long timestamp, List<SourceData> sources) {
            this.answer = answer;
            this.sessionId = sessionId;
            this.timestamp = timestamp;
            this.sources = sources;
        }
    }

    @Getter
    @Setter
    public static class SourceData {
        private int rank;
        private String title;
        private String fileName;
        private String content;
        private String snippet;
        private float score;

        public SourceData(int rank, String title, String fileName, String content, float score) {
            this.rank = rank;
            this.title = title;
            this.fileName = fileName;
            this.content = content;
            this.snippet = content == null || content.length() <= 180
                    ? content
                    : content.substring(0, 180);
            this.score = score;
        }
    }
}
