# XaltarRedstoneGuard

Plugin **Folia 1.21.11** pour limiter les *observer clocks* (observers qui se regardent) et réduire le lag redstone.

## Fonctionnalités

- Détection automatique des observers qui pulsent trop vite
- 3 modes d'action configurables :
  - **Limiter** : détecte les clocks d'observers selon un seuil de ticks
  - **Annuler** : force le signal redstone à 0 si la limite est dépassée
  - **Supprimer** : retire l'observer sans drop d'item si la limite est dépassée
- Nettoyage automatique de la mémoire pour éviter les fuites
- 100% compatible **Folia** (multi-threading par région)

## Installation

1. Télécharge le `.jar` depuis les [Releases](../../releases)
2. Place-le dans le dossier `plugins/` de ton serveur Folia
3. Redémarre le serveur
4. Modifie `plugins/XaltarRedstoneGuard/config.yml` selon tes besoins

## Configuration (`config.yml`)

```yaml
# Seuil en ticks de jeu (1 redstone tick = 2 game ticks)
# Si un observer pulse plus vite que ce seuil, il est considéré comme un clock.
threshold-ticks: 2

# Active la limitation des observers qui pulsent trop vite
limit-observer: true

# Annule le signal redstone si la limite est dépassée (force la puissance à 0)
cancel-observer: true

# Supprime complètement l'observer si la limite est dépassée (sans drop d'item)
break-observer: false

# Intervalle de nettoyage de la mémoire en minutes
cleanup-interval-minutes: 5
```

## Comportements recommandés

| limit-observer | cancel-observer | break-observer | Effet |
|---|---|---|---|
| `true` | `true` | `false` | Signal coupé, bloc reste en place |
| `true` | `true` | `true` | Signal coupé + bloc supprimé (protection max) |
| `false` | * | * | Aucune action |

## Compilation

```bash
mvn clean package
```

Le JAR se trouve dans `target/XaltarRedstoneGuard-<version>.jar`.

## Release automatique (GitHub Actions)

Pousse un tag au format `v*` (ex: `v1.0.0`) et le workflow GitHub Actions compilera automatiquement le plugin et créera une release avec le `.jar` attaché.

```bash
git tag v1.0.0
git push origin v1.0.0
```

## Licence

Projet privé — usage personnel uniquement.
