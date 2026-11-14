# CPV Rapport Hebdo — Premium 2.0

Rapports hebdomadaires Facebook, HTML standalone et Android 6+/10+.

- Données sources filtrées comme les graphiques, dates directement modifiables.
- Mise en forme avancée : blocs, marges, polices, couleurs, alignements, colonnes KPI, logo et disposition des captures. Aperçu, PNG HD et impression PDF.
- OCR entièrement local : Tesseract embarqué dans le HTML ; ML Kit Latin embarqué dans Android. Import de 1 à 12 captures, propositions vérifiables et validation explicite. Boosts de page, publication et événement séparés ; les interactions ne deviennent jamais des followers.
- Date et heure proposées depuis EXIF ou texte de la première capture, sinon date d’importation explicitement signalée. Les métadonnées sont des indications à vérifier ; la période du rapport reste distincte.
- IndexedDB v2 : images séparées des chiffres, dédupliquées par SHA-256. Sauvegardes avec/sans captures ; export d’archive avant retrait confirmé des anciennes captures.
- AppForge Cloud existant : connexion utilisateur, organisation, snapshots GET/PUT avec expectedRevision, découpage des données/captures en fragments inférieurs à 500 Ko, vérification SHA-256 et choix explicite des conflits. Pas de nouveau Worker ni de nouvelle base.
- Android : Download/Backup, Download/CPV, partage de plusieurs cartes, API23/API29, même versionName 2.0 et même clé Release existante.

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

## Synchronisation

Serveur existant : https://appforge-cloud-api.appforge-licences-mg.workers.dev ; routes issues du contrat AppForge Studio : /v1/auth/login, /v1/session, /v1/organizations/{org}/apps/{app}/snapshot. appId racine cpv-hebdo-v1. La connexion et le jeton restent en mémoire ; reconnectez-vous après fermeture complète. L’automatisme s’active dans la session après une première liaison explicite. Une modification concurrente conserve la copie locale et arrête les écritures automatiques.

Le HTML local peut nécessiter une origine autorisée par le Worker (CORS). Android utilise une passerelle HTTPS limitée aux seules routes de ce serveur, sans redirection, sans clé administrateur. La connexion réelle à deux appareils doit être vérifiée avec le compte de l’utilisateur ; aucun identifiant n’est fourni dans ce dépôt.

## Limites à connaître

La reconnaissance de captures petites ou compressées peut échouer : contrôler/corriger avant application. Une heure de barre système sans date ne suffit pas à dater une capture. Les captures conservent la définition de leur source même dans les cartes HD. Les sauvegardes ne sont pas chiffrées ; la session Cloud n’est jamais incluse dedans. L’archivage retire les images de la copie courante après confirmation ; les anciens fragments Cloud ne sont pas purgés automatiquement (aucune suppression serveur implicite).

Les tests navigateur de CI utilisent exclusivement des images synthétiques. Les tests locaux de logique et de rendu ne remplacent pas un essai sur un téléphone réel.
