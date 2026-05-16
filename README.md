# XaltarRedstoneGuard

Plugin **Folia 1.21.11** pour limiter les *observer clocks* (observers qui se regardent), le nombre de blocs redstone par chunk, et les items au sol. Réduit significativement le lag redstone.

## Fonctionnalités

### 🔴 Limitation des Observers
- Détection automatique des observers qui pulsent trop vite
- 3 modes d'action configurables :
  - **Throttle** : limite la fréquence des pulses au seuil configuré
  - **Annuler** : force le signal redstone à 0 si la limite est dépassée
  - **Supprimer** : retire l'observer sans drop d'item si la limite est dépassée
- Nettoyage automatique de la mémoire pour éviter les fuites

### 📦 Limitation des blocs par chunk
- Limite configurable pour **n'importe quel type de bloc** (dynamique via config)
- Comptage en temps réel par chunk
- Message personnalisable avec MiniMessage lorsque la limite est atteinte
- Blocs supportés par défaut : Crafter, Dispenser, Dropper, Repeater

### 🎒 Limitation des items au sol
- Suppression automatique des items les plus anciens lorsque la limite est dépassée
- File FIFO pour une gestion efficace

### 🎮 Commandes

| Commande | Permission | Description |
|---|---|---|
| `/xalguard limit` | `xalguard.limit` | Ouvre un GUI affichant les limites du chunk actuel |
| `/xalguard reload` | `xalguard.reload` | Recharge la configuration et rescanne les chunks chargés |

> **Note** : `/xalguard limit` est accessible à tous les joueurs par défaut. `/xalguard reload` est réservé au staff (OP par défaut).

### 📊 GUI des limites (`/xalguard limit`)
Le menu affiche :
- Un item par type de bloc limité dans le chunk
- Le nombre actuel / la limite configurée
- Une barre de progression visuelle
- Le pourcentage d'utilisation (vert → orange → rouge)

## Installation

1. Télécharge le `.jar` depuis les [Releases](../../releases)
2. Place-le dans le dossier `plugins/` de ton serveur Folia
3. Redémarre le serveur
4. Modifie `plugins/XaltarRedstoneGuard/config.yml` selon tes besoins

## Configuration (`config.yml`)

```yaml
# Seuil en ticks de jeu (1 redstone tick = 2 game ticks)
# Si un observer pulse plus vite que ce seuil, il est considéré comme un clock.
threshold-ticks: 4

# Active la limitation des observers qui pulsent trop vite
limit-observer: true

# Throttle l'observer pour le limiter au tick configuré (plus permissif que cancel)
# Si activé, l'observer peut pulser exactement tous les "threshold-ticks" ticks.
# Si désactivé, la logique "cancel-observer" s'applique.
throttle-observer: true

# Annule le signal redstone si la limite est dépassée (coupe l'output)
cancel-observer: false

# Supprime complètement l'observer si la limite est dépassée (sans drop d'item)
break-observer: false

# Intervalle de nettoyage de la mémoire en minutes
cleanup-interval-minutes: 5

# ============================================================
# Limitation du nombre de blocs par chunk
# ============================================================
block-limits:
  enabled: true

  # Tu peux ajouter n'importe quel bloc ici !
  # Utilise le nom du material Bukkit (ex: repeater, comparator, hopper, piston, etc.)
  crafter: 4000
  dispenser: 4000
  dropper: 4000
  repeater: 1000

  # Message affiché au joueur lorsque la limite est atteinte
  # Placeholders: %type% = nom du bloc, %limit% = limite configurée
  # Supporte les couleurs MiniMessage (<red>, <yellow>, <green>, etc.)
  message: "<red>Limite de <yellow>%type%</yellow> atteinte dans ce chunk (<yellow>%limit%</yellow> blocs)."

# ============================================================
# Limitation du nombre d'items au sol par chunk
# ============================================================
item-limits:
  enabled: true
  max-items-per-chunk: 3000
```

### Ajouter un nouveau bloc
Pour ajouter une limite sur un nouveau bloc, ajoute simplement une ligne dans `block-limits` :

```yaml
block-limits:
  enabled: true
  crafter: 4000
  dispenser: 4000
  dropper: 4000
  repeater: 1000
  comparator: 500      # ← nouveau
  hopper: 200          # ← nouveau
  piston: 500          # ← nouveau
```

Puis exécute `/xalguard reload` pour appliquer les changements sans redémarrer.

## Comportements recommandés (Observers)

| limit-observer | throttle-observer | cancel-observer | break-observer | Effet |
|---|---|---|---|---|
| `true` | `true` | `false` | `false` | Pulse limité au seuil, bloc intact |
| `true` | `false` | `true` | `false` | Signal coupé, bloc reste en place |
| `true` | `false` | `true` | `true` | Signal coupé + bloc supprimé (protection max) |
| `false` | * | * | * | Aucune action |

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
