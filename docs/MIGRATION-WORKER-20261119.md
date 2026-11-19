# CPV 2.4 — migration vers le Worker central canonique

Date : 19 novembre 2026. Application existante conservée ; aucune PWA.

## Correction livrée

- Adresse par défaut unique : `https://appforge-license-api.lalainaadclick.workers.dev` dans HTML, Android et observations CI.
- Les anciennes valeurs du service enregistrées dans le coffre sont remplacées au chargement et avant toute requête. La preuve signée existante est conservée, puis revérifiée auprès du nouveau service. Aucun formulaire ne redemande l’URL, un compte Cloud, un e-mail, un mot de passe ou une organisation.
- Routes et formats conservés : `/v1/config`, `/v1/activate`, `/v1/status`, `/v1/cloud/token`, `/v1/cloud/snapshot?resource=...`. CPV conserve ses ressources fragmentées existantes et leurs révisions ; `default` est également testé sur le serveur réel.
- Identifiant exact : **CPV-HEBDO**. Au premier contrôle du D1 canonique, aucun produit CPV/hebdo n’existait. L’entrée `CPV-HEBDO` / `CPV Rapport Hebdo` a été enregistrée pour cette application existante, puis relue et validée active. Ce n’est pas l’identifiant de Prompti.
- Vérification serveur au lancement, au retour au premier plan et après reconnexion. Hors ligne, l’interface distingue la preuve locale d’une confirmation serveur. Une révocation confirmée efface les jetons actifs et suspend l’accès distant, sans supprimer les rapports, captures ou sauvegardes.
- Jeton Cloud limité dans le temps, transporté en Bearer ; chiffrement Keystore sur Android et conservation en mémoire pour le Cloud HTML. Aucun secret d’administration n’est intégré au client ou au rapport.
- PIN/biométrie facultatifs, chiffrement des sauvegardes au choix, plis de mise en forme, OCR, rapports HD et données existantes conservés.

## Preuve réelle — pas une simulation

[Workflow de vérification du service réel](https://github.com/RMLala/appforg/actions/runs/44497464300), artefact `CPV-canonical-live-worker-evidence`, relevé du 19/11/2026 à 09:33 UTC.

Le test utilise le Worker déployé et son D1 canonique, avec un nouvel essai serveur isolé, un identifiant d’installation aléatoire et les jetons réellement émis. Aucun paiement MVola ni licence de client payant n’a été créé. Aucun `ADMIN_API_TOKEN` n’est employé ; les accès de déploiement existants servent uniquement à l’inscription du produit et au contrôle/nettoyage du seul essai de vérification.

| Contrôle réel | Résultat |
| --- | --- |
| Santé et configuration du nouveau Worker | HTTP 200 / 200 |
| Produit relu dans D1 | CPV-HEBDO, actif |
| Délivrance d’un essai serveur valide | HTTP 200 |
| Vérification du statut | HTTP 200, actif |
| Renouvellement du jeton Cloud | HTTP 200 |
| Renouvellement pour un autre appareil | Refus HTTP 403 |
| Écriture authentifiée du snapshot, ressource default | HTTP 200 |
| Relecture authentifiée et comparaison exacte | HTTP 200, contenu identique |
| Révision obsolète | Refus HTTP 409 |
| Accès avec le même jeton après expiration de cet essai | Refus HTTP 403 |
| Nettoyage de l’espace de snapshots et compteurs de cet essai | Réussi ; données métier existantes non touchées |

La PR backend [appforg #23](https://github.com/RMLala/appforg/pull/23) contient le script de vérification. Les jetons, codes et secrets ne figurent pas dans l’artefact.

## Contrôles automatisés complémentaires

Les tests de l’application vérifient les calculs/imports/exports existants, le coffre chiffré, le maintien des données à la modification du PIN, la restauration sous changement local concurrent, deux contextes IndexedDB séparés, le fonctionnement offline/redémarrage/reconnexion, les conflits et l’absence de duplication. Ils vérifient aussi une licence valide sans droit Cloud, la revérification au retour au premier plan et la suppression des jetons après révocation confirmée.

Le parcours navigateur vérifie qu’une ancienne adresse sauvegardée dans le coffre est migrée sans perdre la preuve de licence, ainsi que l’absence de champs de compte Cloud. Le contrôle Android 13 couvre lancement/PIN facultatif, absence de formulaires Cloud et stockage chiffré du jeton. Les liens des workflows et de la Release signée sont joints à la PR CPV de cette livraison.

## Ce qui reste à valider

- Activation par `/v1/activate` avec un vrai code de licence de test délivré par le gestionnaire et révocation d’une licence payante : aucun code correspondant n’a été fourni, aucun paiement réel n’a été simulé comme preuve.
- Synchronisation entre deux téléphones physiques et reprise après coupure du réseau mobile. Le test serveur réel ci-dessus est authentifié ; les tests multi-contextes/offline restent automatisés.
- Biométrie et partage/restauration Drive sur appareil physique ; réserves FR/MG et commerciales de l’audit précédent.

Les constats 404 du rapport du 18/11 concernent l’ancienne adresse et sont historiques. Le présent relevé prouve une synchronisation authentifiée avec un essai réel sur le nouveau Worker. Il ne certifie pas à lui seul l’ensemble de la conformité V3 ni le parcours d’achat MVola.
