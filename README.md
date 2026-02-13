# 👑 Premium Chat Moderation Plugin

![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.x-brightgreen?style=for-the-badge&logo=minecraft)
![Platform](https://img.shields.io/badge/Platform-PaperMC-blueviolet?style=for-the-badge&logo=papermc&logoColor=white)
![License](https://img.shields.io/badge/License-AGPL(v3)-blue?style=for-the-badge)

---

### ✨ Overview
The **ultimate** chat moderation solution for Minecraft servers. This premium plugin combines **ultra-fast** profanity detection with a robust punishment system, offering dual-API support, database storage, and automated moderation actions.

---

## 🚀 Speed & Performance

*   🚫 **No AI Implementation**: We prioritize raw speed. This plugin does **not** rely on slow AI models.
*   ⚡ **Instant Processing**: Messages are checked and filtered in milliseconds.
*   🌍 **Dual API Support**: Powered by **ProfanityAPI v2** (Main) and **v1** (Fallback) for 99.9% uptime and extreme accuracy.

---

## 🌟 Features

*   🛡️ **Dual Moderation Modes**:
    *   **BLOCK**: Completely prevents the message from being sent.
    *   **CENSOR**: (Requires v2) Replaces profane words with `****` while keeping the rest of the message.
*   📊 **Intelligent Scoring System**:
    *   Assigns a "profanity score" to players based on their behavior.
    *   Scores accumulate over time, triggering stricter punishments for repeat offenders.
*   📉 **Decay System**: Slowly reduces player scores over time to allow for redemption.
*   ⚖️ **Auto-Punishments**:
    *   🔇 **Mute**: Temporarily silence toxicity.
    *   🚫 **Ban**: Automatically ban players who exceed safety thresholds.
    *   👢 **Kick**: Warn players before more severe actions.
*   🔔 **Admin Alerts**: Real-time notifications for staff via Chat or Actionbar.
*   💾 **Database Support**: SQLite storage ensures player data (scores, punishments) is saved permanently.
*   🛠️ **Full Command Suite**: Manually punish users, check status, or toggle alerts.

---

##  Commands

All commands require `chatmoderator.admin` permission unless otherwise noted.

| Command | Description |
| :--- | :--- |
| `/chatmod reload` | 🔄 Reloads all configuration files and database connections. |
| `/chatmod mute <player> <duration> [reason]` | 🔇 Mutes a player. Duration ex: `10m`, `1h`, `perm`. |
| `/chatmod unmute <player>` | 🔊 Unmutes a player. |
| `/chatmod ban <player> <duration> [reason]` | 🚫 Bans a player. Duration ex: `7d`, `perm`. |
| `/chatmod unban <player>` | 🤝 Unbans a player. |
| `/chatmod tempban <player> <duration> [reason]` | ⏳ Alias for ban. |
| `/chatmod status` | 📡 Displays plugin version, API health, and system status. |
| `/chatmod listen` | 👂 Toggles admin alerts for the executor (Requires `chatmoderator.listen`). |

---

## 🔐 Permissions

| Permission | Description | Default |
| :--- | :--- | :--- |
| `chatmoderator.admin` | 🔑 Access to all administration commands coverage | OP |
| `chatmoderator.bypass` | 🛑 Bypasses all chat moderation filters | False |
| `chatmoderator.listen` | 📢 Allows receiving profanity alerts | OP |

---

## ⚙️ Configuration

Full control over every aspect of the plugin via `config.yml`.

```yaml
# ChatModerator Premium Configuration

# Moderation Mode
# Options:
#   BLOCK  - Cancel the message entirely.
#   CENSOR - Replace the message with the cleaned version (only works with v2 API).
#            If v1 fallback is used in CENSOR mode, it will block or use a placeholder depending on availability.
moderation-mode: CENSOR

# Profanity API Settings
profanity-api:
  v2-url: "https://profanity-api.xeven.workers.dev"
  v1-url: "https://vector.profanity.dev"
  timeout-seconds: 5
  
  # Cache settings to reduce API calls
  cache:
    enabled: true
    duration-minutes: 10

# Score System (Premium Feature)
# Each profane message adds its score (0.0 - 1.0) to the player's total.
#
# Score System
# Scores are accumulated 0.0 - 1.0 (probability of profanity).
# When a player reaches a score threshold, punishments are applied.
# Scores are stored in scores.yml.
score-system:
  enabled: true
  min-score-to-flag: 0.9
  
  # Admin Alert Format
  # %score% displays the TOTAL ACCUMULATED SCORE of the player.
  admin-listen:
    type: CHAT # CHAT or ACTIONBAR
    permission: chatmoderator.listen
    format: "&c%player% &7score: &e%score% &8(&f%message%&8)"

  # Punishment Thresholds (Accumulated Score -> Action)
  # Format: "SCORE: COMMAND"
  # Use underscore notation (10_0 = 10.0) to avoid YAML path parsing issues.
  thresholds:
    "10_0": "chatmod mute %player% 1h Auto-Mute: Too much profanity"
    "20_0": "chatmod mute %player% 24h Auto-Mute: Persistent profanity"
    "50_0": "chatmod ban %player% 7d Auto-Ban: Excessive profanity"
    "100_0": "chatmod ban %player% perm Auto-Ban: Permanent"

  # Score Decay
  # Automatically reduce player scores over time to allow redemption.
  decay:
    enabled: true
    # How much score to remove per interval?
    amount: 1.0
    # How often to apply decay? (in hours)
    # Checks are made when player joins.
    interval-hours: 24

# Messages
messages:
  prefix: "&8[&bChatModerator&8] "
  blocked: "&cYour message contains profanity and was blocked."
  censored-notification: "&cYour message was censored."
  
  # Broadcasts (Visible to players with chatmoderator.admin or chatmoderator.listen)
  mute-broadcast: "&8[&cChatModerator&8] &e%player% &7was muted by &e%punisher% &7for &e%duration%&7. Reason: &f%reason%"
  ban-broadcast: "&8[&cChatModerator&8] &e%player% &7was banned by &e%punisher% &7for &e%duration%&7. Reason: &f%reason%"
  
  # Admin alert format is in score-system.admin-listen.format
  
  # Punishments
  mute-reason: "&cYou are muted for %duration%. Reason: %reason%"
  muted-error: "&cYou are muted! Expires in: %time%"
  ban-message: "&cYou have been banned!\n&7Reason: &f%reason%\n&7Expires: &f%time%"
  
  # Commands
  no-permission: "&cNo permission."
  command-usage: "&cUsage: /chatmod <subcommand>"
  reload-success: "&aConfiguration reloaded."
  player-not-found: "&cPlayer not found."
  invalid-duration: "&cInvalid duration. Use 'perm' or time like 10m, 1h, 1d etc."
  cannot-mute-op: "&cYou cannot mute an Operator."
  cannot-mute-bypass: "&cYou cannot mute a player with bypass permissions."
  cannot-ban-op: "&cYou cannot ban an Operator."
  cannot-ban-bypass: "&cYou cannot ban a player with bypass permissions."
  mute-success: "&aMuted %player% for %duration%."
  unmute-success: "&aUnmuted %player%."
  ban-success: "&aBanned %player% for %duration%."
  unban-success: "&aUnbanned %player%."
  
  # Listen Toggle
  listen-enabled: "&aYou are now listening to chat moderation alerts."
  listen-disabled: "&cYou are no longer listening to chat moderation alerts."
  listen-only-players: "&cThis command is only for players."

# Database (SQLite)
database:
  filename: "database.db"

# Update Checker
update-checker: true
```

---

## 📥 Installation

1.  **Download** the `PremiumChatModerator.jar` file.
2.  **Upload** it to your server's `plugins` folder.
3.  **Restart** your server to generate the configuration files.
4.  **Edit** `plugins/PremiumChatModerator/config.yml` to connect to your preferred API and set up the database.
5.  Type `/chatmod reload` or restart your server to apply changes.

---

## 💎 Credits

*   👨‍💻 **Development**: [Obsyron](https://www.obsyron.com)
*   🌐 **Core API**: [ProfanityAPI](https://www.profanity.dev/)
*   🚀 **Advanced API**: [ProfanityAPI V2](https://profanity-api.vercel.app/)
