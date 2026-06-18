package org.example.dto.contest;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ContestChatRequest {

    @JsonAlias("message")
    private String question;

    private List<String> images;

    @JsonProperty("session_id")
    private String sessionId;

    private Boolean stream;
}
