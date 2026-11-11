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

## Build Windows `.exe` via GitHub Actions

Le workflow [`.github/workflows/build-exe.yml`](.github/workflows/build-exe.yml) s'exécute sur `windows-latest` à chaque push/PR sur `main`/`master`, manuellement (`workflow_dispatch`), ou sur tag `v*` :

- **`ImageUtil-windows-installer`** : installeur Windows `.exe` généré avec `jpackage` (inclut un runtime Java minimal via `jlink`, aucun Java requis sur le PC cible).
- **`ImageUtil-windows-portable`** : archive `.zip` contenant `ImageUtil.exe` prêt à lancer sans installation.
- **`ImageUtil-jar`** : JAR exécutable multiplateforme.
- Sur un tag `v*` (ex. `v1.0.0`), une **GitHub Release** est automatiquement créée avec ces fichiers en pièces jointes.

