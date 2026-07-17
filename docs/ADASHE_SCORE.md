# Adashe Score — Score de fiabilité communautaire par IA

## Le problème

Au Cameroun, plus de 70 % de la population est non-bancarisée mais participe massivement
aux tontines (njangis). Deux problèmes concrets :

1. **L'arnaque dans les tontines** — un membre encaisse la cagnotte à son tour puis
   disparaît sans finir de cotiser. Il n'existe aucun moyen de vérifier la fiabilité
   d'une personne avant de l'accepter, à part le bouche-à-oreille.
2. **L'invisibilité financière** — une commerçante qui cotise fidèlement depuis des années
   n'a aucune preuve formelle de sa discipline financière. Son historique n'existe nulle part.

## La solution

Le **Adashe Score** transforme l'historique de participation d'un membre en un score de
confiance (0-100) accompagné d'une **explication en français simple générée par IA**,
consultable par le créateur d'une tontine avant d'accepter un membre.

### Architecture : le score est calculé en Java, l'IA explique

```
GET /api/tontines/{tontineId}/membres/{membreId}/score   (JWT, créateur uniquement)
        │
        ▼
ScoreFiabiliteServiceImpl
        ├─ 1. Agrégation (données existantes, toutes tontines confondues)
        │      cotisations payées · retards · amendes · litiges de tirage · ancienneté
        ├─ 2. Score 0-100 par règles pondérées déterministes (jamais par l'IA)
        │      ponctualité 40 pts · volume d'historique 25 pts · litiges 20 pts · ancienneté 15 pts
        ├─ 3. Cache persistant (table scores_fiabilite) — recalcul seulement si les
        │      données changent (hash SHA-256) ou après 24 h
        └─ 4. Spring AI + Gemini : explication (3 phrases max) + recommandation
               au créateur, en sortie structurée (record Java)
```

**Pourquoi ce découpage ?** Un LLM qui invente un score n'est ni fiable ni reproductible.
Ici le score est transparent et auditable ; l'IA apporte ce qu'elle fait le mieux :
rendre l'information **compréhensible par tous**, y compris les utilisateurs peu à l'aise
avec l'écrit.

### Résilience

- Sans clé API configurée (ou si Gemini est indisponible), le service retourne quand même
  le score chiffré avec une explication générique — l'application ne casse jamais à cause de l'IA.
- Le modèle utilisé est tracé en base (`scores_fiabilite.modele_ia`).

### Niveaux de confiance

| Score | Niveau | Recommandation type |
|---|---|---|
| ≥ 70 | ELEVE | Accepter en confiance |
| 45-69 | MOYEN | Accepter avec suivi régulier |
| < 45 | FAIBLE | Vigilance, privilégier un petit montant |

Un nouveau membre sans historique reçoit un score neutre de 40 (il n'est pas pénalisé,
c'est indiqué dans l'explication).

## Configuration

1. Créer une clé API gratuite : https://aistudio.google.com/app/apikey
2. Définir la variable d'environnement :

```bash
export GEMINI_API_KEY=ta_cle_ici
```

Le modèle utilisé est `gemini-2.5-flash` (palier gratuit du Gemini Developer API),
configuré dans `application.yml` (`spring.ai.google.genai.chat.options.model`).
Grâce à l'abstraction Spring AI (`ChatClient`), changer de fournisseur (Claude, Mistral,
Ollama local…) ne demande qu'un changement de starter Maven et de configuration —
aucune ligne de code métier à modifier.

## Exemple de réponse

```json
{
  "membreId": 12,
  "nomComplet": "Aminatou Bello",
  "score": 87,
  "niveauConfiance": "ELEVE",
  "explication": "Aminatou cotise dans 3 tontines depuis 14 mois. Elle a payé 42 cotisations sur 43 à temps. Son seul retard a été régularisé avec l'amende.",
  "recommandation": "Profil très fiable : vous pouvez l'accepter en confiance.",
  "cotisationsPayees": 42,
  "cotisationsEnRetard": 1,
  "nombreLitiges": 0,
  "ancienneteMois": 14,
  "dateCalcul": "2026-07-17T18:02:11"
}
```

## Vision

À terme, le Adashe Score peut devenir un **historique de crédit alternatif** : une
microfinance partenaire pourrait accorder un prêt sur la base de la discipline de
cotisation démontrée dans l'application — l'inclusion financière par l'IA.
