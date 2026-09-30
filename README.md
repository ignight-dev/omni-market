# ❖ Omni Marketplace (Trading Post)

[![Minecraft NeoForge](https://img.shields.io/badge/Minecraft-NeoForge%201.21.1-orange.svg)](https://neoforged.net/)
[![Java](https://img.shields.io/badge/Java-21-blue.svg)](https://www.oracle.com/java/)
[![Engine](https://img.shields.io/badge/Database-SQLite%20WAL-success.svg)](https://www.sqlite.org/)
[![Status](https://img.shields.io/badge/Status-Production%20Ready-brightgreen.svg)]()

> A state-of-the-art, **Guild Wars 2 inspired MMO Trading Post** built for Minecraft NeoForge 1.21.1. Features order-book matching, instant transactions, persistent offline collection vaults, consumable merchant licenses, personal banking vaults, and an embedded zero-latency SQLite database engine.

---

## ✦ Table of Contents

- [Overview](#-overview)
- [Key Features](#-key-features)
  - [1. Order-Book Market Economy](#1-order-book-market-economy)
  - [2. Physical Merchant System](#2-physical-merchant-system)
  - [3. Consumable Imperial Merchant License](#3-consumable-imperial-merchant-license)
  - [4. Imperial Banker & Personal Vault](#4-imperial-banker--personal-vault)
  - [5. Virtual Emerald Counter & HUD](#5-virtual-emerald-counter--hud)
  - [6. High-Performance SQLite Backend](#6-high-performance-sqlite-backend)
- [Commands Guide](#-commands-guide)
  - [Player Commands](#player-commands)
  - [Administrator Commands](#administrator-commands-permission-level-2)
- [Current Roadmap](#-current-roadmap)
- [Installation & Building](#-installation--building)
- [License & Credits](#-license--credits)

---

## ❖ Overview

Standard Minecraft chest shops and auction houses suffer from chunk loading issues, offline player limitations, and cluttered auction screens. **Omni Marketplace** reimagines trading by implementing an asynchronous **MMO-grade Order Book**:
- **Buyers** place bids (Buy Orders) declaring what they want to pay.
- **Sellers** post listings (Sell Asks) declaring what they want to receive.
- When buy and sell prices intersect, orders match **instantly**—even if both players are on opposite sides of the world or offline!
- Goods and revenue are delivered directly to the player's secure **Collection Vault**, ready for withdrawal at any Trading Post Merchant.

---

## ✦ Key Features

### 1. Order-Book Market Economy
- **Instant Buy & Instant Sell**: Sell immediately to the highest existing buy order or purchase immediately from the cheapest sell listing.
- **Custom Price Setting**: Fine-tune listing prices with micro-step increments (+5 copper per click) or hold to ramp by +50 copper per second.
- **10% Guild Escrow Fee**: Prevents market spam and simulates a realistic trading economy.
- **Full NBT & Component Preservation**: Enchants, durability, custom names, and potion effects are 100% preserved during storage and delivery.
- **Delivery Collection Vault**: Never miss a sale. Bought items and revenue wait safely in your personal vault.

### 2. Physical Merchant System
To protect multiplayer immersion, remote marketplace browsing is restricted by default. Players interact with dedicated NPCs:
- **Trading Post Merchant**: Browse orders, place listings, instant buy/sell, and withdraw goods from your Collection Vault.
- **Grand Merchant Master**: Purchase the **Imperial Merchant License** deed (64 Emerald Blocks) and **Trader's Dispatch Books**.
- **Imperial Banker**: Access your secure bank storage, unlock additional storage rows, incinerate unwanted items, and deposit reserve emeralds.
- **Trader's Dispatch Book (Transceiver)**: Summon a temporary express Trading Post Merchant in the wild for 10 minutes.

### 3. Consumable Imperial Merchant License
- **Deed Activation**: The license is obtained as a soulbound deed from the Grand Merchant Master or an administrator.
- **Right-Click Consumable**: Consuming the deed permanently registers your merchant status in the guild archives (`is_licensed = 1`).
- **Zero Inventory Waste**: Once consumed, the item disappears permanently—no perpetual inventory slot taken!
- **Drop & Transfer Protection**: While physical, the deed cannot be dropped onto the ground, gifted, or placed into external chests. Attempting to drop it safely returns it directly to your hotbar.

### 4. Imperial Banker & Personal Vault
- **Expandable Storage**: Starts with 2 rows (18 slots) and expands up to 6 rows (54 slots) by purchasing upgrades with emeralds/gems:
  - Row 3: `300 Emeralds`
  - Row 4: `500 Emeralds`
  - Row 5: `700 Emeralds`
  - Row 6: `999 Emeralds`
- **Permanent Incinerator (Trash)**: Safely destroy unwanted items with a confirmation dialogue to prevent accidental losses.
- **Imperial Emerald Reserve**: Dedicated high-capacity vault storing up to **9,999,999 Emeralds** outside your regular inventory.

### 5. Virtual Emerald Counter & HUD
- **Auto-Absorption**: Physical emeralds picked up in the world automatically absorb into your virtual pocket counter (up to 999 emeralds).
- **Physical Overflow**: Emeralds collected beyond the 999 cap overflow naturally into your inventory as physical items.
- **Dynamic HUD Display**: An in-game HUD displays your current pocket balance in real time.
- **Physical Emerald Withdrawal**:
  - Click the emerald HUD icon or press <kbd>Z</kbd> to drop 1 physical emerald to the ground.
  - <kbd>Shift</kbd> + click to drop a full stack of 64 physical emeralds.

### 6. High-Performance SQLite Backend
- **WAL Mode (Write-Ahead Logging)**: High-concurrency database operations without server hitching.
- **Crash Resilient**: Transactions and balances are atomic and survive server restarts or crashes.
- Stored under `<world>/data/omni_marketplace.db`.

---

## ❖ Commands Guide

### Player Commands

| Command | Aliases | Description |
| :--- | :--- | :--- |
| `/market` | `/market balance`, `/market check` | Displays your current purse balance and vault status. |
| `/market open` | — | Opens the market GUI (requires proximity to a merchant or OP bypass). |
| `/market vault` | `/market claim` | Claims pending vault items and coins (requires merchant proximity or OP bypass). |

> *Note: `/marketplace`, `/tradingpost`, and `/omni market` can be used interchangeably.*

---

### Administrator Commands (Permission Level 2+)

All admin commands are accessible via `/market admin`, `/marketplace admin`, `/tradingpost admin`, or `/omni market admin`:

#### NPC Spawning
```bash
/market admin spawn merchant    # Spawns a permanent Trading Post Merchant
/market admin spawn master      # Spawns a permanent Grand Merchant Master
/market admin spawn banker      # Spawns a permanent Imperial Banker
```

#### Items & Remote Access
```bash
/market admin open                         # Force-opens the Trading Post GUI remotely
/market admin give <player> [amount]       # Gives Trader's Dispatch Books (1-64)
/market admin give_license [player]        # Grants an Imperial Merchant License deed
```

#### Merchant License Management
```bash
/market admin reset_license                # Revokes & resets license for the executing admin
/market admin reset_license <player>       # Revokes license for a target player or selector (@a, @p)
/market admin reset_license all            # Revokes licenses for ALL accounts and clears active players
/market admin reset_license name <name>    # Revokes license for an offline player account by name
```

---

## ✦ Current Roadmap

### Phase 1: Core Foundation ✅
- [x] Full Order-Book matching engine (Sell Asks & Buy Orders).
- [x] Guild Vault collection delivery system.
- [x] Multi-tier currency conversion (Gold, Silver, Copper).
- [x] SQLite WAL database integration.
- [x] Physical NPC merchants & express dispatch books.

### Phase 2: Banking, Currency & Usability ✅
- [x] Personal Banker GUI with expandable 6-row storage.
- [x] Incinerator / Trash system with confirmation.
- [x] Imperial Emerald Reserve (up to 9,999,999 emeralds).
- [x] Virtual pocket emerald counter (max 999) with hotbar HUD.
- [x] Drop keybind (<kbd>Z</kbd>) and HUD click controls.
- [x] Consumable Imperial Merchant License with drop loss fix.
- [x] Comprehensive administrator license reset & revocation suite.

### Phase 3: Market Intelligence & Analytics 🔄 (In Progress)
- [ ] Historical price charts & 24h volume graphs in GUI.
- [ ] Discord Webhook integration for high-value sales and market alerts.
- [ ] Category-based tax configuration per world or dimension.
- [ ] Search filter presets (Favorites, Watchlist, Guild-only).

### Phase 4: Network & Cross-Server Sync 📋 (Planned)
- [ ] Optional Redis / MySQL backend for BungeeCord / Velocity networks.
- [ ] Cross-server global marketplace synchronization.
- [ ] Web-based market dashboard for viewing live orders outside Minecraft.

---

## ❖ Installation & Building

### Requirements
- **Minecraft**: 1.21.1
- **Mod Loader**: NeoForge `21.1.x`
- **Java**: 21+

### Building From Source
```bash
git clone https://github.com/ignight-dev/omni-market.git
cd omni-market
./gradlew build
```
The compiled mod JAR will be located in `build/libs/omni_marketplace-1.0.0.jar`.

---

## ❖ License & Credits

Developed with ❤️ for the Minecraft community. Inspired by the **Guild Wars 2 Trading Post** economy.
Distributed under the MIT License.
