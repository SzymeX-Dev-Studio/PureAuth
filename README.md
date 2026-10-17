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
