# ImageUtil

Utilitaire Java Swing simple et fonctionnel pour redimensionner et compresser une ou plusieurs images (JPG, PNG, BMP, GIF).

## Fonctionnalités

- **Sélection multiple** : ajout de fichiers, ajout d'un dossier complet, ou glisser-déposer direct dans la liste.
- **Redimensionnement** :
  - Aucun redimensionnement (compression seule)
  - Pourcentage (`%`)
  - Dimensions max (conserve les proportions)
  - Dimensions exactes (px)
  - Option pour autoriser ou bloquer l'agrandissement
- **Compression & conversion** :
  - Sortie en **JPEG** (qualité réglable de 10 % à 100 %), **PNG** (sans perte), ou **Format d'origine**
  - Gestion automatique de la transparence lors de la conversion vers JPEG (fond blanc)
- **Destination** :
  - Par défaut dans un sous-dossier `compressed` à côté des images d'origine
  - Ou dans un dossier spécifique avec suffixe optionnel et gestion des collisions

## Prérequis & exécution locale

- **Java 21** et **Maven**

```bash
mvn test package
java -jar target/ImageUtil.jar
```

## Build Windows `.exe` en local avec Docker (`buildx`)

Un [`Dockerfile`](Dockerfile) multi-étapes permet de compiler et générer l'exécutable Windows `.exe` (via le profil Maven `-Pexe` avec Launch4j) ainsi qu'une archive portable incluant un JRE Windows 64-bit :

```bash
docker buildx build --platform linux/amd64 --output type=local,dest=./dist .
```

Les fichiers générés dans `./dist/` :
- `ImageUtil.exe` (utilise `./jre` s'il est présent à côté, sinon le Java 21 système)
- `ImageUtil-windows-portable.zip` (`ImageUtil.exe` + `jre/` Windows 64-bit prêt à lancer sans Java installé)
- `ImageUtil.jar`

## Build Windows `.exe` via GitHub Actions

Le workflow [`.github/workflows/build-exe.yml`](.github/workflows/build-exe.yml) s'exécute sur `ubuntu-latest` via le [`Dockerfile`](Dockerfile) uniquement manuellement (`workflow_dispatch`) ou lors du push d'un tag `v*` :

- **`ImageUtil-windows-exe`** : exécutable Windows `ImageUtil.exe` généré via Launch4j.
- **`ImageUtil-windows-portable`** : archive `.zip` contenant `ImageUtil.exe` + JRE Windows 64-bit prêt à lancer sans Java préinstallé.
- **`ImageUtil-jar`** : JAR exécutable multiplateforme.
- Sur un tag `v*` (ex. `v1.0.0`), une **GitHub Release** est automatiquement créée avec ces fichiers en pièces jointes.

