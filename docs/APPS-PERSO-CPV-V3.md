# APPS PERSO — CPV Hebdo — audit de reprise V3

Date : 17 novembre 2026 (Madagascar).
Application existante conservée, aucune PWA produite.

## Référentiel et preuves

- Standard lu : https://github.com/RMLala/android-template/blob/main/APPFORGE-STANDARD-V3.md
- Blob du standard consulté : ac2ceeebcad0e332ccb6f3ae1d079aaee8bef331.
- Modèle et politique CI : https://github.com/RMLala/android-template/blob/main/README.md
- Dépôt existant vérifié : https://github.com/RMLala/cpv-hebdo (privé).
- PR existante : https://github.com/RMLala/cpv-hebdo/pull/1 (ouverte, non fusionnée).
- Commit distant audité : 3aa589b7549cd866f1dd6ba0841c581946d982c7.
- CI : https://github.com/RMLala/cpv-hebdo/actions/runs/43613445143

## État vérifié

| Domaine | Développé | Preuve / reste à faire |
| --- | --- | --- |
| Rapport, KPI, graphiques, dates, captures | Oui | Tests locaux relancés, 50+ contrôles et 10 tests passent |
| OCR, IndexedDB, sauvegarde, exports HD | Oui | Job quality réussi, y compris tests navigateur |
| Android Debug API23/API29 | Oui | Job debug réussi et artefacts présents, non expirés lors de l'audit |
| Release signées API23/API29 | Configuré | Job échoué : quatre secrets de signature absents du contexte CI |
| Sync AppForge avec conflits | Client développé | Pas de preuve de test réel entre deux appareils, ni de validation des quotas/révocations serveur |
| Drive via partage et sélecteur | Parcours fichiers/partage présent | Vérification sur téléphone avec Drive à réaliser |
| Chiffrement facultatif des sauvegardes | Non | À développer et tester avec mot de passe erroné / données altérées |
| PIN obligatoire / biométrie prioritaire | Non | À intégrer ; ne pas présenter un verrou HTML comme sécurité du stockage |
| Licences et essai 3 jours anti-réinitialisation | Non | Contrat serveur à vérifier et activation/révocation à tester |
| À propos & Support V3, FR/MG | Incomplet | Intégrer les informations réelles, aide/FAQ, confidentialité/CGU, consentement diagnostic, mises à jour ; pas d'historique des versions dans À propos |
| Icônes adaptatives / multi-densités | Incomplet | Icône vectorielle actuelle ; assets de lancement à contrôler sur appareil |

Artefacts vérifiés : Debug API23 14121082857, Debug API29 14120534237,
HTML/QA 14120379234. Ces artefacts concernent le commit distant ci-dessus,
pas une future version conforme V3.

## Travail préparé dans cette reprise

Branche locale dédiée : work/cpv-v3-20261117.
Le workflow est adapté à la politique du modèle : aucun secret de signature en PR,
signature uniquement sur main ou lancement manuel depuis main.
Les fonctions métier sont conservées. Aucun nouveau push, build distant ni fusion
n'a été effectué dans cette reprise avant la sélection des formats.

La copie Git locale historique a des identifiants de commits différents des commits
distants (publication initiale via API). Avant publication, appliquer les changements
sur la tête distante vérifiée, sans écraser l'historique.

## Trois prochaines actions

1. Recueillir les formats de livraison et la décision explicite de visibilité.
   GitHub Free est confirmé pour RMLala. Le dépôt reste privé en attendant une
   décision ; aucune clé ni capture personnelle ne doit être rendue publique.
2. Compléter sécurité, licences, chiffrement, support et FR/MG sur les sources
   existantes, avec tests des migrations et contrats AppForge réels.
3. Publier par branche/PR, exécuter les contrôles CI adaptés, vérifier les artefacts
   choisis et les parcours sur appareil ; transmettre cet état au pilotage global.

Statut : conformité V3 NON acquise. Validation utilisateur et tests d'intégration
encore nécessaires. Cette fiche est préparée pour le pilotage APPS PERSO ; elle
n'est pas déclarée transmise à un autre chat ou outil sans preuve de transmission.


## Mise à jour d’implémentation — 2.1

Choix utilisateur : HTML autonome et Release signée API29 seulement. Passage public
explicitement autorisé ; GitHub exige une réauthentification du compte myapp-bot.
Le dépôt demeure privé tant que cette vérification n’est pas terminée.

Développé : coffre AES-GCM (rapports/captures), migration transactionnelle, chiffrement
facultatif des exports avec intégrité, partage natif sans OAuth, PIN Android natif
avec HMAC protégé Keystore et temporisation persistante, biométrie prioritaire avec
PIN de secours, page Support/FAQ/confidentialité/CGU/diagnostic consenti, vérification
des releases GitHub, icônes adaptatives/multi-densités. Client de licence adapté au
contrat réel de RMLala/appforg : essai/activation/statut, signature ECDSA et liaison
à l’installation ; preuve locale chiffrée, révocation et grâce hors ligne bornée.

Tests locaux : contrôles existants et tests AES-GCM, intégrité et migration passent.
Les nouveaux tests navigateur et le test de lancement Android 13 restent à exécuter
par la nouvelle CI. Chromium local est bloqué par les sockets de l’environnement.

Limites restantes : configuration et enregistrement du produit côté service licences,
validation anti-abus après réinstallation (surtout HTML), vérification réelle de la
licence et de la sync à deux appareils/quotas/révocation, essai biométrique et Drive
sur téléphone, traduction complète FR/MG et logo officiel AppForge. Aucun statut de
conformité V3 complète ni de licence active n’est revendiqué.
