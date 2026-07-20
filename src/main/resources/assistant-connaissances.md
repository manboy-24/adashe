# Base de connaissances Assistant Adashe
# Source : guide d'utilisation in-app (Profil → Aide & Support → Guide) + règles métier.
# Modifiable côté serveur sans mise à jour de l'application.

## Démarrer
- Créer un compte : numéro camerounais (6XXXXXXXX), code de vérification envoyé par email (ou par SMS via Google/Firebase si pas d'email), nom complet, code PIN à 4 chiffres.
- Se connecter : numéro de téléphone + PIN à 4 chiffres. La biométrie (empreinte/visage) peut remplacer le PIN une fois activée.
- Configurer le portefeuille : Profil → Portefeuille → ajouter un numéro MTN MoMo ou Orange Money (les deux possibles). Le portefeuille est OBLIGATOIRE pour rejoindre ou créer une tontine.
- Les numéros configurés dans une tontine se synchronisent automatiquement avec le portefeuille (et inversement, ils sont pré-remplis au moment de payer).

## Créer une tontine
- Tableau de bord → « + Créer une tontine » : nom, montant de cotisation, fréquence (hebdomadaire, mensuelle, annuelle), type de tirage (aléatoire ou fixe/rotatif).
- Le créateur devient automatiquement administrateur.
- Avant de démarrer : configurer les numéros Mobile Money de réception et éventuellement la commission de gestion (0 à 10 %, 0 % par défaut). La commission est VERROUILLÉE après le démarrage et va intégralement à l'administrateur.
- L'amende de rattrapage (montant fixe, 200 FCFA par défaut) est aussi configurable avant le démarrage.
- Démarrage : minimum 2 membres et au moins un numéro Mobile Money configuré.

## Rejoindre une tontine
- Par code d'invitation : Accueil → Rejoindre → saisir le code à 8 caractères (majuscules/minuscules acceptées). L'adhésion est immédiate.
- Par invitation : l'administrateur invite avec le numéro de téléphone ; le membre accepte via la notification. L'invitation EXPIRE après 24 heures si elle n'est pas acceptée.
- Un portefeuille Mobile Money configuré est requis dans les deux cas.

## Paiements (Mobile Money uniquement)
- Cotiser : onglet Cotisations → Payer → choisir MTN MoMo ou Orange Money. Le numéro du portefeuille est pré-rempli automatiquement.
- Une fenêtre de confirmation s'affiche sur le téléphone à débiter — le payeur entre son code secret Mobile Money pour valider. Pas besoin de composer un code USSD.
- Des frais de traitement Mobile Money s'ajoutent au montant (affichés avant confirmation).
- Statuts : En attente (jaune) → Payé (vert) ; Annulé ou En retard sinon.
- L'administrateur peut payer une cotisation POUR un membre (bouton « Payer pour lui ») : le paiement part alors du Mobile Money de l'administrateur et il est tracé comme payé pour le compte du membre.
- Rattrapage d'un cycle impayé : possible à tout moment, avec l'amende fixe de la tontine (200 FCFA par défaut) ajoutée au montant.
- Si le paiement reste « En attente » : vérifier le solde du compte Mobile Money et la fenêtre de confirmation sur le téléphone ; la demande expire après quelques minutes et peut être relancée.
- Reçu PDF : Profil → Historique des reçus (téléchargeable et partageable).

## Retards et blocage
- Un retard est compté si la cotisation n'est pas payée au moment du tirage du cycle.
- À 2 retards, le membre est BLOQUÉ automatiquement dans la tontine. Pour être débloqué : contacter l'administrateur et régulariser les cycles impayés (rattrapage + amendes).

## Tirages
- Seul l'administrateur lance le tirage (un seul par cycle), et uniquement À PARTIR DU DERNIER JOUR du cycle — avant cette date, le bouton est refusé.
- Type aléatoire : le gagnant est tiré au sort parmi les membres n'ayant pas encore reçu. Type rotatif/fixe : l'ordre de passage est défini à l'avance (modifiable par l'admin avant le démarrage).
- Le gagnant a 15 minutes pour accepter ou décliner (silence = acceptation). S'il décline, l'admin choisit un remplaçant.
- Un tirage peut être contesté (signalement) dans les 15 minutes ; l'administrateur tranche le litige.
- La cagnotte est virée automatiquement sur le Mobile Money du gagnant après confirmation, moins la commission de l'admin si elle a été configurée.
- Les autres membres voient un bandeau d'attente pendant la fenêtre de confirmation.

## Adashe Score (fiabilité)
- Score 0-100 calculé sur : ponctualité des paiements, volume d'historique, litiges, ancienneté. Niveaux : ÉLEVÉ (≥70), MOYEN (45-69), FAIBLE (<45).
- Visible par les administrateurs uniquement — y compris AVANT d'inviter quelqu'un (aperçu par numéro de téléphone dans l'écran d'ajout de membre).
- Comprend une explication en langage simple, une recommandation et un risque estimé de retard au prochain cycle.
- Le score remonte en payant ses cotisations à temps ; il baisse avec les retards, les amendes et les litiges.

## Briefing hebdomadaire (administrateurs)
- Chaque dimanche à 18h, l'administrateur reçoit une notification résumant la semaine de chaque tontine active : collecte, membres à jour, retardataires, action recommandée.

## Statistiques et exports
- Menu Stats : vue globale (toutes tontines) et vue par tontine — collecte, taux de recouvrement, ponctualité, graphiques.
- Export PDF : rapport complet (membres, cotisations, tirages), depuis le menu Stats.
- Export Excel : tableau des cotisations, compatible Excel et Google Sheets.

## Notifications
- Notifications push pour : nouveau membre, cotisation reçue, tirage, gagnant, rappels de paiement, score critique, briefing hebdo, virements.
- Réglages : Profil → Notifications pour activer/désactiver par type.
- Les rappels de cotisation sont envoyés automatiquement avant la fin du cycle.

## Sécurité et compte
- PIN oublié : écran de connexion → réinitialisation par email (lien sécurisé).
- Après 5 tentatives de PIN incorrectes, le compte est temporairement bloqué (15 minutes) et une alerte est envoyée par email et notification.
- Connexion depuis un nouvel appareil : un code de vérification est demandé (email requis).
- Changer de numéro Mobile Money : Profil → Portefeuille (PIN requis).
- Modifier son PIN : Profil → Sécurité & PIN (ancien PIN requis).
- Ne jamais partager son PIN, même avec un proche. L'équipe Adashe ne le demandera jamais.
- Déconnexion : Profil → Se déconnecter. Les sessions sur d'autres appareils peuvent être coupées en réinitialisant le PIN.

## Profil
- Modifier son nom, avatar et email : Profil → en-tête du profil.
- Historique des reçus PDF : Profil → Historique des reçus.
- Soutenir Adashe : Profil → faire un don au développeur (Mobile Money).

## Questions fréquentes
- « Je n'ai pas reçu la fenêtre de paiement » : vérifier que le numéro saisi correspond bien à l'opérateur choisi (numéro MTN pour MTN MoMo, Orange pour Orange Money) et que le téléphone est allumé avec du réseau.
- « Numéro invalide » au paiement : le numéro ne correspond pas à l'opérateur sélectionné — corriger le numéro ou changer d'opérateur.
- « Je ne peux pas lancer le tirage » : le tirage n'est possible qu'à partir du dernier jour du cycle, et tous les membres doivent être identifiables ; le message d'erreur indique la date exacte.
- « Le membre invité n'apparaît pas » : l'invitation expire après 24 h — renvoyer une invitation si besoin.
- « Comment quitter une tontine ? » : contacter l'administrateur ; un membre ne peut pas quitter seul une tontine démarrée (l'équilibre des tours en dépend).

## Support humain
- Email : babstore24@gmail.com · WhatsApp : +237 681 951 580 (7j/7, 8h-20h, heure de Yaoundé).
