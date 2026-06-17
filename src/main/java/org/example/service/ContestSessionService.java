package org.example.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class ContestSessionService {

    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    @Value("${contest.max-history-pairs:6}")
    private int maxHistoryPairs;

    public SessionState getOrCreate(String sessionId) {
        String normalizedSessionId = sessionId == null || sessionId.isBlank()
                ? UUID.randomUUID().toString()
                : sessionId;
        return sessions.computeIfAbsent(normalizedSessionId, SessionState::new);
    }

    public final class SessionState {
        private final String sessionId;
        private final List<Map<String, String>> history = new ArrayList<>();
        private final ReentrantLock lock = new ReentrantLock();

        private SessionState(String sessionId) {
            this.sessionId = sessionId;
        }

        public String getSessionId() {
            return sessionId;
        }

        public List<Map<String, String>> snapshot() {
            lock.lock();
            try {
                return new ArrayList<>(history);
            } finally {
                lock.unlock();
            }
        }

        public void addExchange(String userQuestion, String assistantAnswer) {
            lock.lock();
            try {
                history.add(message("user", userQuestion));
                history.add(message("assistant", assistantAnswer));
                int maxMessages = Math.max(1, maxHistoryPairs) * 2;
                while (history.size() > maxMessages) {
                    history.remove(0);
                    if (!history.isEmpty()) {
                        history.remove(0);
                    }
                }
            } finally {
                lock.unlock();
            }
        }

        private Map<String, String> message(String role, String content) {
            Map<String, String> message = new HashMap<>();
            message.put("role", role);
            message.put("content", content);
            return message;
        }
    }
}
