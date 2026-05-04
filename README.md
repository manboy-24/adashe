# Tontine+ Backend — Spring Boot + PostgreSQL

## Stack technique
| Composant | Technologie |
|---|---|
| Framework | Spring Boot 3.2 (Java 17) |
| Base de données | PostgreSQL |
| Authentification | JWT + PIN 4 chiffres |
| Paiements | CinetPay (MTN Money + Orange Money Cameroun) |
| Notifications push | Firebase Cloud Messaging |
| Documentation API | Swagger UI |

---

## Architecture des packages
```
src/main/java/com/tontine/
├── TontineApplication.java
├── config/
│   ├── SecurityConfig.java      — Spring Security + CORS + JWT filter
│   ├── MobileMoneyConfig.java   — Config MTN & Orange (lue depuis application.yml)
│   └── OpenApiConfig.java       — Swagger / OpenAPI 3
├── controller/
│   ├── AuthController.java      — Inscription, OTP, PIN, reset PIN
│   ├── TontineController.java   — CRUD tontines, membres, cotisations, tirage
│   ├── PaiementController.java  — Mobile Money (MTN + Orange)
│   └── NotificationController.java
├── entity/                      — Entités JPA (tables PostgreSQL)
├── enums/                       — Énumérations métier
├── dto/request|response/        — Objets d'échange API
├── repository/                  — Spring Data JPA
├── security/                    — JwtService + JwtAuthFilter + UserDetailsService
├── service/impl/                — Logique métier complète
│   ├── AuthServiceImpl          — Inscription + OTP
│   ├── PinAuthServiceImpl       — Connexion PIN + reset
│   ├── TontineServiceImpl       — Tontines, membres, cotisations, tirage
│   ├── PaiementServiceImpl      — Initiation + callback CinetPay
│   ├── MtnMobileMoneyService    — API MTN MoMo directe
│   ├── OrangeMoneyService       — API Orange Money directe
│   └── NotificationServiceImpl  — Push + SMS + email
└── util/
    ├── SecurityUtil.java        — Récupère l'ID utilisateur depuis le contexte
    └── OtpUtil.java             — Génération de codes OTP sécurisés
```

---

## Flux d'authentification (connexion par PIN)

```
1. [Mobile] POST /auth/inscrire
        { nom, prenom, telephone, email? }
        → SMS envoyé avec code OTP 6 chiffres

2. [Mobile] POST /auth/verifier-otp
        { telephone, code }
        → téléphone vérifié, JWT temporaire retourné

3. [Mobile] POST /auth/pin/creer   [JWT requis]
        { pin: "1234", confirmPin: "1234" }
        → PIN enregistré, JWT final retourné → CONNECTÉ

──────── PROCHAINES CONNEXIONS ─────────────────────────────────

4. [Mobile] POST /auth/pin/connexion
        { telephone, pin }
        → JWT retourné → CONNECTÉ

──────── PIN OUBLIÉ ─────────────────────────────────────────────

5. [Mobile] POST /auth/pin/reset/demande
        { telephone, canal: "SMS" | "EMAIL" }
        → OTP envoyé

6. [Mobile] POST /auth/pin/reset/confirmer
        { telephone, codeOtp, nouveauPin, confirmPin }
        → Nouveau PIN enregistré + connexion automatique
```

---

## Règles d'accès aux tontines

| Action | Qui peut ? |
|---|---|
| Voir la liste de ses tontines | Membre ou Créateur (UNIQUEMENT les siennes) |
| Voir le détail d'une tontine | Membres du groupe uniquement |
| Créer une tontine | Tout utilisateur connecté |
| Ajouter/retirer un membre | Créateur ou Admin |
| Voir les cotisations | **Créateur uniquement** |
| Enregistrer une cotisation | **Créateur uniquement** |
| Lancer le tirage | **Créateur uniquement** |
| Voir l'historique des tirages | Tous les membres |
| Voir les statistiques | **Créateur uniquement** |

---

## Paiements Mobile Money (Cameroun)

### Via CinetPay (recommandé — supporte MTN + Orange en une API)
```
POST /api/paiements/mobile-money
{
  "membreId": 12,
  "tontineId": 3,
  "montant": 10000,
  "operateur": "MTN_MOBILE_MONEY",   // ou "ORANGE_MONEY"
  "numeroPaiement": "+237691000000"
}
→ L'utilisateur reçoit une notification USSD sur son téléphone
→ CinetPay appelle le webhook /api/paiements/webhook/cinetpay
→ La cotisation est automatiquement enregistrée
```

### Opérateurs supportés
- `MTN_MOBILE_MONEY` — MTN Cameroon (préfixes 65x, 67x, 68x)
- `ORANGE_MONEY` — Orange Cameroon (préfixes 69x)
- `ESPECES` — Enregistrement manuel en cash par le créateur

---

## Démarrage rapide

### Prérequis
- Java 17+
- PostgreSQL 14+
- (Optionnel) Compte CinetPay sandbox : https://cinetpay.com

### 1. Créer la base de données
```sql
CREATE DATABASE tontine_db;
CREATE USER tontine_user WITH PASSWORD 'tontine_pass';
GRANT ALL PRIVILEGES ON DATABASE tontine_db TO tontine_user;
```

### 2. Configurer les variables d'environnement
```bash
export DB_USERNAME=tontine_user
export DB_PASSWORD=tontine_pass
export JWT_SECRET=VG9udGluZVBsdXNTZWNyZXRLZXkyMDI0Q2FtZXJvdW4=
export CINETPAY_API_KEY=votre-api-key
export CINETPAY_SITE_ID=votre-site-id
```

### 3. Lancer l'application
```bash
mvn clean spring-boot:run
```

### 4. Documentation Swagger
```
http://localhost:8080/api/swagger-ui.html
```

---

## Endpoints principaux

### Auth
| Méthode | URL | Description |
|---|---|---|
| POST | /auth/inscrire | Inscription (envoie OTP) |
| POST | /auth/verifier-otp | Vérifier OTP |
| POST | /auth/pin/creer | Créer son PIN (JWT requis) |
| POST | /auth/pin/connexion | **Connexion principale** |
| POST | /auth/pin/reset/demande | Demander reset PIN |
| POST | /auth/pin/reset/confirmer | Nouveau PIN + OTP |
| POST | /auth/refresh-token | Rafraîchir le JWT |

### Tontines
| Méthode | URL | Description |
|---|---|---|
| GET | /tontines | Mes tontines uniquement |
| POST | /tontines | Créer une tontine |
| GET | /tontines/{id} | Détail (membres uniquement) |
| POST | /tontines/rejoindre?code=XYZ | Rejoindre par code |
| POST | /tontines/{id}/membres | Ajouter membre |
| POST | /tontines/cotisations | Enregistrer cotisation (créateur) |
| GET | /tontines/{id}/cotisations | Voir cotisations (créateur) |
| POST | /tontines/{id}/tirage | Lancer tirage (créateur) |
| GET | /tontines/{id}/tirages | Historique tirages (membres) |
| GET | /tontines/{id}/statistiques | Stats (créateur) |

### Paiements
| Méthode | URL | Description |
|---|---|---|
| POST | /paiements/mobile-money | Payer par MTN ou Orange |
| GET | /paiements/{ref}/statut | Vérifier statut paiement |
| GET | /paiements/mes-paiements | Mes paiements |
| POST | /paiements/webhook/cinetpay | Webhook CinetPay (auto) |
