# CPV Rapport Hebdo — Premium 2.4

Rapports hebdomadaires Facebook, HTML standalone et Android 6+/10+.

- Données sources filtrées comme les graphiques, dates directement modifiables.
- Mise en forme avancée : blocs, marges, polices, couleurs, alignements, colonnes KPI, logo et disposition des captures. Aperçu, PNG HD et impression PDF.
- OCR entièrement local : Tesseract embarqué dans le HTML ; ML Kit Latin embarqué dans Android. Import de 1 à 12 captures, propositions vérifiables et validation explicite. Boosts de page, publication et événement séparés ; les interactions ne deviennent jamais des followers.
- Date et heure proposées depuis EXIF ou texte de la première capture, sinon date d’importation explicitement signalée. Les métadonnées sont des indications à vérifier ; la période du rapport reste distincte.
- IndexedDB v2 : images séparées des chiffres, dédupliquées par SHA-256. Sauvegardes avec/sans captures ; export d’archive avant retrait confirmé des anciennes captures.
- AppForge Cloud existant : connexion utilisateur, organisation, snapshots GET/PUT avec expectedRevision, découpage des données/captures en fragments inférieurs à 500 Ko, vérification SHA-256 et choix explicite des conflits. Pas de nouveau Worker ni de nouvelle base.
- Android : Download/Backup, Download/CPV, partage de plusieurs cartes, API23/API29, même versionName 2.1 et même clé Release existante.

## Construction et vérification

```
python3 tools/build.py
npm ci
npm test
npx playwright install --with-deps chromium
npm run test:browser
gradle :app:assembleApi23Debug :app:assembleApi29Debug
```

Le générateur assemble un unique HTML avec les ressources OCR épinglées par URL/version et SHA-256. Aucun fichier personnel, token ou valeur de secret dans les sources. Les captures Facebook personnelles sont exclues ; restaurer une sauvegarde complète pour les transférer à Android.

Les quatre noms de secrets existants restent KEYSTORE_BASE64, KEY_ALIAS, KEYSTORE_PASSWORD, KEY_PASSWORD. L’organisation RMLala utilise GitHub Free : les secrets d’organisation nécessitent ici un dépôt public et une politique d’accès appropriée. Si ces secrets sont absents, le job Release échoue explicitement ; il ne produit pas de faux APK signé.

## Synchronisation AppForge V3

Le code courant (`src/license.js` et `src/cloud.js`) utilise le Worker central AppForge V3 :

- URL fixe : https://appforge-license-api.lalainaadclick.workers.dev
- Produit déclaré dans le client : `CPV-HEBDO` (son existence et ses droits dans D1 doivent être confirmés par l’éditeur ; la configuration publique ne publie pas la liste des produits).
- Configuration : `GET /v1/config`.
- Essai / activation : `POST /v1/trial` et `POST /v1/activate`.
- Contrôle : `POST /v1/status`.
- Jeton Cloud lié à la licence et à l’installation : `POST /v1/cloud/token`.
- Snapshot : `GET/PUT /v1/cloud/snapshot?resource=default`, avec contrôle de révision et gestion explicite des conflits.

Le client ne demande pas de compte Cloud, d’e-mail, de mot de passe ni d’organisation. Les opérations métier restent locales hors connexion ; la synchronisation reprend lorsque le réseau et une preuve de licence valide sont disponibles. Les erreurs de licence ou de Cloud ne doivent jamais supprimer les données locales.

Les anciennes références à `appforge-cloud-api.appforge-licences-mg.workers.dev`, à `/v1/auth/login` et aux routes d’organisation décrivent le protocole historique, plus utilisé par le client V3. Le smoke test central documenté au 22/11/2026 confirme `/health`, `/v1/config` et le refus 401 d’un snapshot sans jeton ; il ne valide pas une activation CPV réelle ni un échange D1 authentifié. Ne pas déclarer la synchronisation CPV de production certifiée sans test avec une licence dédiée et deux appareils.

## Limites à connaître

La reconnaissance de captures petites ou compressées peut échouer : contrôler/corriger avant application. Une heure de barre système sans date ne suffit pas à dater une capture. Les captures conservent la définition de leur source même dans les cartes HD. Les sauvegardes peuvent être chiffrées avec un mot de passe distinct ; la session Cloud n’est jamais incluse dedans. L’archivage retire les images de la copie courante après confirmation ; les anciens fragments Cloud ne sont pas purgés automatiquement (aucune suppression serveur implicite).

Les tests navigateur de CI utilisent exclusivement des images synthétiques. Les tests locaux de logique et de rendu ne remplacent pas un essai sur un téléphone réel.


## Reprise V3 — version 2.1

Livrables sélectionnés : HTML standalone et Release API29 signée. Les variantes Debug
API23/API29 restent des contrôles CI internes ; aucune livraison Debug/API23/EXE/PWA
supplémentaire n’est demandée. La signature ne reçoit aucun secret en PR ; elle est
réservée à main/lancement manuel sur main. Le journal APPS PERSO et les écarts sont
consignés dans docs/APPS-PERSO-CPV-V3.md. La conformité complète n’est pas acquise.

Coffre local AES-GCM : PIN demandé dans le HTML, PIN natif de secours et biométrie
prioritaire sur Android. La clé Android est conservée sous chiffrement Keystore,
le PIN ne traverse pas la passerelle WebView. Aucun PIN/jeton en clair dans les sources.
Un PIN HTML court reste exposé aux essais hors ligne ; un code long est recommandé.
Les fichiers exportés sans chiffrement et le HTML lui-même ne sont pas protégés.

Le service de licence et le produit doivent être fournis/configurés par l’éditeur.
Aucune URL inventée et aucune auto-attribution de licence propriétaire. Le protocole
client suit cloud/license-worker/src/index.js de RMLala/appforg. Aucun secret admin.
Le serveur contrôle paiement, essai, appareil et révocation ; la preuve signée reste
limitée par expiration et une revérification après 7 jours. L’anti-abus d’un essai
après effacement total du stockage HTML reste une limite à résoudre côté serveur.
