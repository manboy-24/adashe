package com.tontine.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class TirageWebSocketHandler extends TextWebSocketHandler {

    // tontineId → sessions connectées
    private final Map<Long, Set<WebSocketSession>> sessionsByTontine = new ConcurrentHashMap<>();

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long tontineId = extractTontineId(session);
        sessionsByTontine
                .computeIfAbsent(tontineId, k -> ConcurrentHashMap.newKeySet())
                .add(session);
        log.debug("WS connecté tontine={} session={}", tontineId, session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long tontineId = extractTontineId(session);
        Set<WebSocketSession> sessions = sessionsByTontine.get(tontineId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) sessionsByTontine.remove(tontineId);
        }
        log.debug("WS déconnecté tontine={} session={}", tontineId, session.getId());
    }

    /** Diffuse un événement à tous les membres connectés pour cette tontine. */
    public void broadcast(Long tontineId, TirageWsEvent event) {
        Set<WebSocketSession> sessions = sessionsByTontine.getOrDefault(tontineId, Collections.emptySet());
        if (sessions.isEmpty()) return;

        String json;
        try {
            json = mapper.writeValueAsString(event);
        } catch (Exception e) {
            log.error("WS sérialisation échouée", e);
            return;
        }

        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(message);
                } catch (Exception e) {
                    log.warn("WS envoi échoué session={}", session.getId(), e);
                }
            }
        }
    }

    private Long extractTontineId(WebSocketSession session) {
        String path = session.getUri().getPath();
        return Long.parseLong(path.substring(path.lastIndexOf('/') + 1));
    }
}
