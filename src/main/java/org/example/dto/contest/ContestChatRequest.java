package org.example.dto.contest;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ContestChatRequest {

    private String question;

    private List<String> images;

    @JsonProperty("session_id")
    private String sessionId;

    private Boolean stream;
}
