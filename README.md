---

# 🔐 PureAuth

**A configurable authentication system for Minecraft servers and networks.**

Protect player accounts with password or PIN login, two-factor authentication, account recovery and progressive account locks. PureAuth brings together account protection, proxy synchronization and administration tools, with editable English and Polish messages.

Built for **Paper/Purpur offline-mode servers**, with dedicated modules for **Velocity** and **BungeeCord**.

## ✨ Features at a glance

- 🔐 Password registration, login and password management
- 🔢 Configurable PIN authentication through an inventory GUI
- 📱 TOTP two-factor authentication and backup recovery codes
- 🛡️ Admin Guard requiring 2FA for designated staff accounts
- 🔑 Account recovery through security questions
- ⏳ Progressive temporary and permanent account locks
- 👻 Player hiding and interaction restrictions before authentication
- 💎 Optional Premium Auto-Login with a same-IP requirement
- 🔄 Short reconnect sessions tied to the player’s IP and account security state
- 🌐 Proxy synchronization and Redis network support
- ⚖️ Fair Play Guard for administrator-led player inspections
- 📢 Configurable Discord webhook notifications
- 🗄️ SQLite and MySQL/MariaDB storage
- 💾 Scheduled backups with configurable retention
- 🧩 Optional PlaceholderAPI integration
- 🌍 Editable English and Polish messages

## 🔐 Flexible authentication

Players can register and log in using passwords, or enable **PIN login** through a configurable inventory GUI. Passwords, PINs and recovery answers are stored as hashes.

Optional registration CAPTCHA and account limits per IP help control account creation. Repeated failed authentication attempts can escalate from a temporary lock to a longer lock and finally a permanent account lock.

Administrators can unlock accounts using the plugin’s management commands.

## 📱 Two-factor authentication & Admin Guard

Players can add **TOTP-based 2FA** using compatible authenticator apps such as:

- Google Authenticator
- Aegis
- 2FAS

After password or PIN verification, accounts with 2FA require an authenticator code. Backup codes provide a recovery option when access to the authenticator is lost.

**Admin Guard** can require designated staff accounts to configure 2FA before gaining access to the game. It prevents those accounts from disabling mandatory 2FA and requires a new setup after backup-code recovery.

## 👻 Protection before login

Unauthenticated players remain *hidden from other players* *[WIP]* and cannot freely interact with the server.

Restrictions cover movement, chat, commands, inventory changes, item drops and pickups, attacks, entity interactions, hand swapping, item consumption, portals and mounting. Authentication commands and the PIN GUI remain available where required.

Protection remains active during mandatory Admin Guard setup, with a configurable authentication timeout.

## 🔑 Recovery & reconnect sessions

Players can configure a security question and use it to recover their account. Successful password recovery removes a forgotten PIN, while configured 2FA still applies.

Optional reconnect sessions reduce repeated logins. Sessions are checked against the player’s IP and account security state, so security changes invalidate older sessions.

Premium Auto-Login is also available, with a configurable same-IP requirement.

## 🌐 Server networks [WIP - Work In Progress]

PureAuth includes separate modules for **Paper/Purpur**, **Velocity** and **BungeeCord**.

Proxy-to-backend authentication synchronization uses a shared secret to authenticate synchronization messages. **Redis mode** provides cross-proxy username coordination and shared reconnect sessions.

Networks can choose how Redis failures affect new connections, including a fail-closed policy.

## ⚖️ Fair Play Guard

Give administrators a controlled workflow for player inspections:

- Set a dedicated inspection location
- Teleport and restrict the selected player
- Keep inspection chat private between the player and inspector
- Display configurable instructions and reminders
- Enforce an inspection-area radius
- Apply a persistent restriction after disconnecting during an inspection
- Clear the inspection and restore the player’s state

## ⚙️ Configuration & administration

Customize chat messages, titles, actionbars, kick messages and PIN GUI text through separate **English and Polish message files**.

Discord webhooks can report selected account and administrative events. Sensitive authentication commands are suppressed from standard server command logs, while PureAuth’s administrative audit redacts password and PIN arguments.

Scheduled backups, configurable database pools and optional PlaceholderAPI support help integrate PureAuth into your server setup.
