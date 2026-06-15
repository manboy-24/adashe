package com.tontine.websocket;

import com.tontine.dto.response.TirageResponse;

/**
 * Événement WebSocket émis aux membres lors d'un tirage.
 * type = "TIRAGE_LANCE" quand l'admin lance, "TIRAGE_CONFIRME" quand il confirme.
 */
public record TirageWsEvent(String type, TirageResponse tirage) {}
