package com.tontine.service;

public interface AssistantService {

    /**
     * Répond à une question d'aide en s'appuyant sur la base de connaissances
     * (miroir du guide in-app) et le contexte des tontines de l'utilisateur.
     *
     * @param question question en langage naturel (max 500 caractères)
     * @param userId   utilisateur connecté (transmis par le contrôleur — invariant CLAUDE.md)
     */
    String repondre(String question, Long userId);
}
