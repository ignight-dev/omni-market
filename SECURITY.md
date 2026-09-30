# 🛡️ Security Policy — Omni Marketplace

The Omni Marketplace development team is committed to ensuring the economic integrity, data security, and stability of the marketplace engine across all single-player worlds and multiplayer server deployments.

This document outlines our security policies, supported versions, vulnerability disclosure procedures, and the architectural safeguards built into the mod to protect server economies against exploitation, duplication, and unauthorized data modification.

---

## ✦ Supported Versions

Security updates, bug fixes, and anti-exploit patches are actively maintained for the following versions:

| Version | Minecraft Version | Mod Loader | Support Status |
| :--- | :--- | :--- | :--- |
| **1.0.x** | **1.21.1** | **NeoForge 21.1.x+** | 🟢 **Actively Supported** |
| < 1.0.0 | <= 1.20.x | Forge / Fabric | 🔴 **End of Life (Unsupported)** |

> [!IMPORTANT]
> If you are running an outdated version of Omni Marketplace, please update to the latest release before submitting a security report, as the issue may have already been resolved.

---

## ✦ Reporting a Vulnerability

We treat all security vulnerabilities with the highest priority. If you discover an exploit, dupe glitch, packet vulnerability, or database flaw, **do NOT disclose it publicly** (such as in public GitHub issues, Discord chats, or forums). Public disclosure puts live server economies at immediate risk.

### Disclosure Channels
Please report security issues through any of the following private channels:

1. **GitHub Private Vulnerability Reporting** (Preferred):
   - Navigate to the [Security Advisory tab](https://github.com/ignight-dev/omni-market/security/advisories) on GitHub.
   - Click **"Report a vulnerability"** to open a confidential report.
2. **Direct Security Contact**:
   - Email: **`rocostre@outlook.com`** or **`rd386598@gmail.com`**
   - Subject: `[SECURITY] Omni Marketplace Exploit Report - <Brief Summary>`

---

### What to Include in Your Report
To help us diagnose and remediate the issue quickly, please include:
- **Type of Vulnerability**: (e.g., Currency Duplication, Item Duping, Packet Injection, Denial of Service, NBT Corruption, Permission Bypass).
- **Environment**: Minecraft version, NeoForge build, Omni Marketplace version, and any relevant server environment (Singleplayer, Dedicated Server, Multi-server).
- **Detailed Step-by-Step Reproduction**: Exact sequence of actions or packet exchanges required to trigger the issue.
- **Proof of Concept (PoC)**: Logs, video demonstration, or network packet traces (if applicable).
- **Impact Assessment**: How the exploit affects the server economy, database, or player experience.

---

### Response & Remediation Timetable
- **Initial Acknowledgment**: Within **24 to 48 hours**.
- **Triage & Reproduction**: Within **3 to 5 business days**.
- **Patch Development & Testing**: High-severity issues (e.g., infinite money/item dupes) are prioritized for emergency patch release within **7 days**.
- **Public Disclosure**: A security advisory and change note will be published only after a patched release is made available to the public.

---

## ✦ Security Architecture & Threat Model

Omni Marketplace is engineered from the ground up with defensive MMO architecture to defend against common Minecraft modding vulnerabilities:

### 1. Server-Authoritative Execution
- **Zero Client Authority**: The client never dictates prices, purse balances, item delivery, or transaction completion. 
- All client-to-server packets (`BuyOrderC2S`, `InstantBuyC2S`, `ClaimVaultC2S`, etc.) are treated strictly as untrusted *requests*.
- The server independently validates player proximity, purse balances, inventory contents, item legality, and licensing status before executing database transactions.

### 2. Atomic Database Transactions & Anti-Duping
- **ACID-Compliant SQLite Engine**: The embedded SQLite engine operates in Write-Ahead Logging (`WAL`) mode with full thread synchronization using Java `ReentrantLock`.
- **Atomic Escrow Exchange**:
  - When placing a Buy Order, the buyer's copper/emerald currency is immediately debited into escrow.
  - When posting a Sell Ask, the seller's physical item is immediately removed from inventory into escrow.
  - Transactions cannot produce phantom coins or duplicated items if a server crashes mid-trade.
- **Delivery Collection Vault**: All matched goods and currency are stored in database records (`vault_items` and `vault_currency`). Items only leave the database upon verified player inventory insertion.

### 3. SQL Injection Prevention
- **100% Parameterized Prepared Statements**: Every SQL query across [DatabaseManager.java](src/main/java/com/omni/marketplace/db/DatabaseManager.java) strictly utilizes `java.sql.PreparedStatement` with parameterized placeholders (`?`).
- Player usernames, item IDs, and NBT data strings are never concatenated directly into SQL statements, completely eliminating SQL injection vectors.

### 4. Network Packet Security & Bounds Checking
- **Strict ByteBuf Decoders**: Custom packet payloads (`MarketPackets.java`) use strict VarInt, VarLong, and UTF-8 decoders with bounds enforcement.
- **Payload Sanitization**:
  - Quantity bounds: Checked against `[1, 64000]`.
  - Price bounds: Checked against `[1, 9,223,372,036,854,775,807]`.
  - String length bounds: Sanitized to prevent memory exhaustion / buffer overflow attacks.
- **Rate-Limiting & Interaction Range**: Remote packet commands verify whether the sender has administrative bypass (`hasPermission(2)`) or is within physical reach (`<= 6.0 blocks`) of a valid merchant NPC.

### 5. Soulbound & Item Tampering Safeguards
- **Drop & Toss Prevention**: The Imperial Merchant License cannot be dropped, placed in external containers (chests, hoppers, shulkers, barrels), or handed to entities.
- **Ghost Item Protection**: If a drop packet is received, the item is restored directly into the hotbar and resynced via `inventoryMenu.broadcastFullState()`, preventing client-side desync or disappearing item exploits.
- **One-Time Consumable Verification**: License deeds can only be consumed once. Redundant consumption checks prevent wasting deeds or race-condition double activation.

### 6. Administrative Privilege Enforcement
- All administrative subcommands (`/market admin spawn`, `/market admin give`, `/market admin reset_license`) require Minecraft Operator **Permission Level 2** or higher.
- Console and remote RCON commands are supported with dedicated syntax checks.

---

## ✦ Administrator Security Best Practices

For server administrators running Omni Marketplace on public multiplayer servers:

1. **Protect Database Files**:
   - Ensure the server host OS restricts filesystem access to `<world>/data/omni_marketplace.db`.
   - Never expose SQLite `.db`, `.db-wal`, or `.db-shm` files over unsecured web servers.
2. **Routine Economy Backups**:
   - Implement automated world backups. The SQLite database is saved alongside standard world saves.
3. **Audit Market Activity**:
   - Server administrators can query the `trade_history` table in `omni_marketplace.db` to inspect trading logs, trade types, timestamps, fees paid, and participating player UUIDs.
4. **Operator Access**:
   - Restrict Permission Level 2+ to trusted staff members only, as `/market admin reset_license` and `/market admin give` permit administrative market intervention.

---

## ✦ Out of Scope

The following scenarios are considered outside the scope of Omni Marketplace security advisories:
- Attacks requiring existing Root, OS-level, or Server Console / RCON access.
- Exploits caused by running unauthorized third-party bytecode patchers or hacked server jars.
- Distributed Denial of Service (DDoS) targeting the host network layer.
- Bugs or exploits caused by client-side cheat software modifying client-side render state (the server remains authoritative).

---

## ✦ Acknowledgments & Hall of Fame

We deeply appreciate security researchers and community administrators who responsibly disclose vulnerabilities. Valid, responsibly disclosed security reports will be credited here and in the official mod release notes.
