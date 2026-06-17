package org.example.dto.contest;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

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

        public ChatData(String answer, String sessionId, long timestamp) {
            this.answer = answer;
            this.sessionId = sessionId;
            this.timestamp = timestamp;
        }
    }
}
