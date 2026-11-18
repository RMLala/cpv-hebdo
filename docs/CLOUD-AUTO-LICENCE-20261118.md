# CPV 2.3 — Cloud automatique lié à la licence

État au 18 novembre 2026. Cette livraison ne certifie pas la conformité V3 complète.

## Références vérifiées

- Standard officiel : [APPFORGE-STANDARD-V3.md](https://github.com/RMLala/android-template/blob/ea5e9bce72817f43ec7b3f6b3e979c02ce46b480/APPFORGE-STANDARD-V3.md), PR [android-template #7](https://github.com/RMLala/android-template/pull/7) fusionnée.
- Contrat du backend : [appforg PR #8](https://github.com/RMLala/appforg/pull/8), source épinglée au commit `8f2786fa56442508cc4582501f622b03135ebfb9`.
- Worker testé : `cloud/license-worker/src/index.js`, migrations 0001 à 0006. Les copies dans `tests/fixtures/issuer/` sont des fixtures du code officiel, pas un nouveau service déployé.

## Ce qui existait

CPV 2.2 conservait les rapports et captures dans IndexedDB chiffré, avec sauvegardes JSON facultativement chiffrées, OCR local et exports HD. Le PIN et la biométrie étaient facultatifs. Le Cloud utilisait des instantanés fragmentés, des empreintes de contenu et des révisions, mais exigeait e-mail, mot de passe et organisation. La licence signée avait un écran de configuration manuelle du service.

## Ce qui est développé

- Retrait du formulaire de compte Cloud, du choix d’organisation et de la configuration serveur côté client.
- Service officiel et produit `CPV-HEBDO` configurés dans le code public ; ces valeurs ne sont pas des secrets. Aucun secret administrateur n’est fourni.
- Activation/vérification de licence, obtention d’un jeton Cloud signé lié à la licence, au produit et à l’installation, renouvellement automatique et reprise après 401. Un refus serveur suspend l’accès distant sans effacement local.
- Contrôle de signature, d’expiration, d’appareil, de produit et de périmètre avant de considérer un jeton reçu comme valide. La date utilisable est bornée par l’expiration de licence.
- Android : jeton chiffré AES-GCM avec clé Keystore liée à l’application et à l’appareil ; aucun getter JavaScript du jeton stocké. Requêtes limitées au service officiel, sans redirections. HTML : jeton Cloud uniquement en mémoire, renouvelé après fermeture ; la preuve de licence reste dans le coffre local. Sans PIN, le navigateur conserve sa clé locale : ce n’est pas une sécurité native.
- Synchronisation activée par une licence éligible sans compte client. État réel, dernière synchronisation, erreurs, bouton manuel et résolution explicite des conflits.
- File persistante coalescée : l’état local et l’indicateur d’attente sont sauvegardés ensemble dans IndexedDB. Reprise réseau avec temporisation progressive et contrôle périodique.
- Fragments identifiés par empreinte et écriture conditionnelle de la racine : pas de doublon logique lors d’une reprise. Une première liaison à une copie distante existante demande le choix de la copie à conserver. Un changement de licence ne transfère pas silencieusement les données déjà liées à une autre licence.
- Restauration validée avant remplacement, garde contre une saisie concurrente et conservation des données en cas d’erreur. Empreintes calculées sur des données normalisées afin d’éviter de faux conflits après restauration.
- Jetons, code de licence, identifiant d’installation et métadonnées de liaison exclus des instantanés Cloud et sauvegardes. Les jetons ne passent ni dans les URL ni dans les journaux.
- Rapports, captures, calculs, sauvegardes au choix, PIN facultatif et groupes de mise en forme repliables conservés. Aucune PWA.

## Tests réellement exécutés

Les tests d’intégration utilisent le code officiel du Worker, ses migrations SQL, le vrai moteur SQLite (`node:sqlite`), la signature ECDSA et deux contextes applicatifs avec IndexedDB distincts. Les clés, clients, licences et confirmations de paiement sont générés exclusivement pour le test. Ce ne sont ni un D1 de production ni des téléphones physiques.

| Exigence | Exécution automatisée | Validation de production |
| --- | --- | --- |
| Activation après paiement confirmé | Création refusée sans confirmation ; création/activation acceptée avec paiement fictif marqué confirmé | Paiement MVola réel non effectué |
| Cloud sans compte client | Client relié au Worker sans e-mail, mot de passe ni organisation ; parcours navigateur et contrôle de l’écran Android sans formulaire | Licence de production non fournie |
| Délivrance / renouvellement | Jeton émis, vérifié et renouvelé via les vraies routes du Worker | À exercer sur le Worker déployé |
| Expiration / révocation | Licence expirée, produit suspendu et révocation refusent l’accès sur chaque opération | À exercer sur le D1 réel |
| Appareil non autorisé | Troisième appareil refusé ; renouvellement pour un autre appareil refusé ; appareil retiré bloqué | À exercer sur appareils autorisés réels |
| Deux appareils | Deux clients avec installations et IndexedDB distincts partagent la même licence et les mêmes données | Deux téléphones non testés |
| Hors ligne | Modification conservée avec réseau désactivé | Essai physique restant |
| Reprise après reconnexion | Redémarrage du contexte avec stockage conservé puis renouvellement et synchronisation | Réseau mobile réel restant |
| Pertes / doublons / conflits | Reprises répétées sans nouveaux fragments, conflit sans écrasement, saisie pendant restauration conservée | À confirmer sur D1 réel |
| Isolation des licences | Une autre licence ne voit pas les instantanés ; jeton modifié refusé ; client refuse un périmètre d’une autre licence | Audit de production restant |

S’ajoutent les contrôles existants : calculs, import/export, chiffrement, intégrité, PIN facultatif, plis après changement de style, OCR hors ligne, cartes HD, compilations Android et démarrage Android 13. Les résultats des workflows liés à la PR font foi ; un job vert ne vaut pas validation métier en production.

## Anomalies, limites et dépendances

1. Au début de l’audit, les routes automatiques étaient présentes dans la PR centrale #8, mais absentes de `appforg/main`. Le dépôt CPV ne déploie pas le Worker central. Son déploiement et la migration D1 `0006_cloud_snapshots.sql` doivent être confirmés par le pilotage AppForge Studio.
2. Les requêtes directes depuis l’environnement de travail ont reçu HTTP 403, y compris `/health` et `/v1/config`. Cela ne prouve pas l’état du Worker : une restriction réseau intermédiaire peut intervenir. Le job **Deployed backend observation (not business validation)** conserve une observation séparée depuis GitHub Actions, sans utiliser de licence réelle ni modifier de données métier. Observation effective du 18/11 à 12:10 UTC (workflow 44301449773) : `/health` et `/v1/config` répondent 200 ; `/v1/cloud/token` et `/v1/cloud/snapshot` répondent 404. Le nouveau contrat Cloud n’est donc pas exposé par ce déploiement à cet instant.
3. Complément serveur préparé dans [appforg PR #11](https://github.com/RMLala/appforg/pull/11), empilé sur #8 : migration additive 0007, droit Cloud individuel, quotas cumulés/journaliers et quota d’appareils atomiques, routes admin GET/PATCH. Tests Worker + SQLite réussis localement, aucun déploiement ni mutation D1 de production. Le client distingue une licence valide sans droit Cloud d’une licence révoquée. Le contrat épinglé accorde le Cloud aux licences actives/payées et aux essais valides. Il contrôle les appareils et un plafond de 450 Ko par requête. Il ne fournit pas de réglage individuel des droits Cloud ni de quota cumulé de stockage par licence : les contrôles préparés par #11 doivent être intégrés et déployés avant de vendre des offres à quotas différenciés. Les valeurs techniques par défaut y sont configurables et ne constituent pas une offre commerciale.
4. Les instantanés du Worker officiel ne sont pas chiffrés de bout en bout. Les accès passent par HTTPS et les contrôles serveur. Les fragments devenus non référencés n’ont pas de collecte automatique dans ce contrat.
5. Les anciennes copies Cloud par organisation ne sont ni effacées ni automatiquement migrées. Leurs données absentes du dernier état local demandent une migration administrateur ou un export contrôlé. Les rapports et captures locaux sont conservés.
6. Le produit `CPV-HEBDO` doit exister dans AppForge Studio avec les licences/appareils correspondants. Aucune licence de production n’a été inventée pour annoncer une activation réelle.
7. Biométrie et Drive sur téléphone physique, traduction complète FR/MG et autres réserves de l’audit précédent restent hors de cette validation Cloud.

## Prochaines actions du pilotage

1. Finaliser le backend central : PR #8 et #11, migrations 0006/0007 et déploiement, droits et quotas réellement proposés, sauvegarde D1 et contrôle des routes publiques.
2. Exécuter le parcours de production avec une licence après paiement MVola validé et deux appareils autorisés, puis expiration/révocation et isolation ; joindre les preuves sans secrets.
3. Valider sur téléphone l’APK signé, la reprise réseau et les parcours de sauvegarde/restauration ; décider de la migration des anciennes copies Cloud par organisation.

Les commits, PR, workflows et artefacts de cette livraison sont reliés dans la conversation de PR. Statut : client implémenté et testé sur fixtures officielles ; nouvelles routes Cloud constatées absentes du service déployé (404) ; validation métier de production non déclarée.
