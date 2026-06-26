# AdasheCash Backend — Guide pour Claude

Spring Boot 3.2 · Java 17 · PostgreSQL · JWT · Firebase FCM · CinetPay

---

## Invariants critiques — à ne jamais violer

### 1. Ne jamais appeler `securityUtil.getCurrentUserId()` deux fois dans la même requête

`SecurityUtil.getCurrentUserId()` fait un appel base de données (`utilisateurRepository.findByTelephone`). Les contrôleurs l'appellent une fois et transmettent l'ID en paramètre aux services. Les méthodes de service doivent utiliser ce paramètre — jamais faire un deuxième appel interne.

```java
// ✅ Correct — le contrôleur transmet userId, le service l'utilise
public TontineResponse getMesTontines(Long userId, Pageable pageable) {
    // ...
    return tontines.stream().map(t -> toResponseBatch(t, userId)).collect(...);
}

// ❌ Interdit — double appel SecurityContext dans le service
public TontineResponse getMesTontines(Long userId, Pageable pageable) {
    Long currentUserId = securityUtil.getCurrentUserId(); // appel redondant
    // ...
}
```

Ce bug a causé une régression majeure : `estCreateur = false` pour toutes les tontines. Fix appliqué dans `TontineServiceImpl` (voir historique git).

### 2. `MembreTontineRole` — seuls CREATEUR et MEMBRE sont utilisés en pratique

L'enum `MembreTontineRole` contient `CREATEUR`, `ADMIN`, `MEMBRE`. En pratique :
- `CREATEUR` : assigné à la création de la tontine
- `MEMBRE` : assigné à l'ajout/acceptation d'un membre
- `ADMIN` : **jamais assigné actuellement** — réservé pour usage futur

### 3. Mapping des rôles dans les DTOs (intentionnel)

Le backend mappe délibérément CREATEUR → `"ADMIN"` dans `MembreResponse.role` (pour unifier la gestion côté mobile). Le client Android ne doit **jamais** recevoir la chaîne `"CREATEUR"`.

Le boolean `estCreateur` dans `TontineResponse` est le seul indicateur fiable pour le client Android.

```java
// Dans TontineServiceImpl.toResponseBatch() :
boolean estCreateur = userId != null && membresRaw.stream()
    .anyMatch(m -> m.getUtilisateur().getId().equals(userId)
            && m.getRoleMembreTontine() == MembreTontineRole.CREATEUR);
```

### 4. `@Transactional(readOnly = true)` sur les méthodes de lecture

Les méthodes de lecture dans `TontineServiceImpl` portent `@Transactional(readOnly = true)`. Ne pas enlever cette annotation — elle active les optimisations Hibernate sur les requêtes en lecture.

---

## Architecture des packages

```
src/main/java/com/tontine/
├── config/
│   ├── SecurityConfig.java      JWT filter + CORS
│   ├── MobileMoneyConfig.java   MTN & Orange (application.yml)
│   └── OpenApiConfig.java       Swagger / OpenAPI 3
├── controller/
│   ├── AuthController.java      Inscription, PIN, reset PIN, Google
│   ├── TontineController.java   CRUD tontines, membres, cotisations, tirage
│   ├── PaiementController.java  Mobile Money (MTN + Orange + CinetPay)
│   └── NotificationController.java
├── entity/                      Entités JPA
├── enums/                       MembreTontineRole, StatutTontine, StatutMembre…
├── dto/request|response/        DTOs échange API
├── repository/                  Spring Data JPA
├── security/                    JwtService · JwtAuthFilter · UserDetailsService
├── service/impl/
│   ├── AuthServiceImpl          Inscription + OTP + déconnexion
│   ├── PinAuthServiceImpl       Connexion PIN + reset
│   ├── TontineServiceImpl       Tontines, membres, cotisations, tirage (fichier principal)
│   ├── PaiementServiceImpl      Initiation + callback CinetPay
│   ├── MtnMobileMoneyService    API MTN MoMo directe
│   ├── OrangeMoneyService       API Orange Money directe
│   └── NotificationServiceImpl  Push FCM + SMS + email
└── util/
    ├── SecurityUtil.java        getCurrentUserId() — DB call, appeler une seule fois par requête
    └── OtpUtil.java             Génération codes OTP sécurisés
```

---

## Flux de paiement CinetPay (asynchrone)

```
1. POST /paiements/mobile-money
        { membreId, tontineId, montant, operateur, numeroPaiement }
        → PaiementServiceImpl initie la transaction CinetPay
        → L'utilisateur reçoit une notification USSD sur son téléphone

2. [Utilisateur confirme sur son téléphone]

3. POST /paiements/webhook/cinetpay  ← appelé automatiquement par CinetPay
        → Vérifie la signature
        → Enregistre la cotisation si paiement confirmé
        → Envoie notification push FCM au créateur
```

Le webhook est l'unique point d'entrée pour la confirmation. Ne jamais confirmer un paiement Mobile Money côté serveur sans passer par le webhook.

---

## Sécurité

- JWT Bearer token dans le header `Authorization`
- `SecurityContextHolder` (ThreadLocal) : valide uniquement pendant la durée de la requête HTTP
- `SecurityUtil.getCurrentUserId()` ne fonctionne que dans un thread portant un contexte Spring Security — ne pas appeler depuis un thread async (`@Async`, scheduler)
- Les endpoints sous `/auth/**` (inscription, OTP, connexion PIN) sont publics
- Tout le reste requiert un JWT valide

---

## Pièges connus

| Symptôme | Cause |
|---|---|
| `estCreateur = false` pour toutes les tontines | Double appel `securityUtil.getCurrentUserId()` dans le service |
| `LazyInitializationException` | Accès à une relation `@ManyToOne` lazy hors session — utiliser `JOIN FETCH` dans la requête JPQL |
| Webhook CinetPay ignoré | URL de webhook mal configurée dans le dashboard CinetPay, ou signature invalide |
| OTP jamais reçu | Variable d'env SMS non configurée, ou numéro sans préfixe `+237` |
