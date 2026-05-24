![VChat Banner](https://raw.githubusercontent.com/Vlabs99/Vchat/main/docs/banner.png)

# VChat

Modern realtime Android messaging platform built with Java, Firebase, and scalable modular architecture.

![Platform](https://img.shields.io/badge/Platform-Android-00C853?style=for-the-badge)

![Language](https://img.shields.io/badge/Language-Java-orange?style=for-the-badge)

![Backend](https://img.shields.io/badge/Backend-Firebase-yellow?style=for-the-badge)

![Architecture](https://img.shields.io/badge/Architecture-Realtime-blue?style=for-the-badge)

![Status](https://img.shields.io/badge/Status-Active_Development-purple?style=for-the-badge)

---

# Download APK

📦 Latest APK  
https://github.com/Vlabs99/Vchat/releases/download/v1.0/app-debug.apk

---

# Screenshots

| Splash | Chat List |
|---|---|
| ![](public/screenshots/vchat-splash.jpeg) | ![](public/screenshots/vchat-chat-list.jpeg) |

| Private Chat | Group Chat |
|---|---|
| ![](public/screenshots/vchat-private-chat.jpeg) | ![](public/screenshots/vchat-group-chat.jpeg) |

| Group Settings | Profile |
|---|---|
| ![](public/screenshots/vchat-group-settings.jpeg) | ![](public/screenshots/vchat-profile.jpeg) |

| User Search |
|---|
| ![](public/screenshots/vchat-user-search.jpeg) |

---

# Features

## Messaging System
- Realtime messaging
- Direct one-to-one chats
- Group chats
- Reply to message
- Forward message
- Pinned messages
- Message pagination
- Typing indicator
- Realtime Firestore synchronization
- Realtime message updates

---

## Group System
- Group creation
- Add members to group
- Remove members from group
- Group admin messaging control
- Group system messages
- Group resurrection after new messages
- Group chat visibility restoration
- Hidden/deleted group recovery

---

## Relationship System
- Friend requests
- Accept friend request
- Reject friend request
- Remove friend
- Block user
- Unblock user
- Friend-only messaging restrictions
- Relationship state management
- Pending request handling

---

## Lifecycle & Restriction System
- Delete chat for me
- Fresh chat reopen lifecycle
- Old hidden history isolation
- Restriction banners
- Composer visibility control

---

# Architecture

VChat follows a modular realtime architecture focused on scalability, lifecycle safety, and realtime synchronization.

## Core Systems

- Realtime Firestore listener system
- Modular manager/helper architecture
- Restriction precedence engine
- Relationship synchronization layer
- Lifecycle-safe chat restoration
- Thread-safe UI update handling
- Presence and typing infrastructure
- Group resurrection flow
- Realtime conversation synchronization

---

## Main Managers

- ChatComposerController
- ReplyManager
- ForwardManager
- TypingManager
- FriendManager
- GroupManager
- ChatManager

---

# Firebase Integration

- Firebase Authentication
- Firebase Firestore
- Firebase Storage
- Cloud Functions groundwork
- FCM groundwork partially implemented

---

# Tech Stack

## Android
- Java
- Android Studio
- Material Design

## Backend & Cloud
- Firebase Authentication
- Firebase Firestore
- Firebase Storage
- Firebase Cloud Functions

## Architecture
- Realtime Listener Architecture
- Manager / Helper Modular Design
- Lifecycle-safe synchronization

---

# Installation

## Clone Repository

```bash
git clone https://github.com/Vlabs99/Vchat.git
```

## Open Project

Open the project using:

- Android Studio Hedgehog or newer

---

## Firebase Setup

Add your Firebase configuration file:

```text
app/google-services.json
```

Enable:
- Firebase Authentication
- Firestore Database
- Firebase Storage

---

## Run Project

```bash
Sync Gradle
Run App
```

---

# Future Roadmap

- Firebase Cloud Messaging (FCM)
- Heads-up notifications
- Lockscreen notifications
- Media messaging
- Poll messages
- Event messages
- Contact sharing
- Sticker support
- Active chat anti-spam system

---

# Developer

**Vishvarajsinh Chudasama**

MCA Student • Android Developer • Realtime Systems Enthusiast

Focused on scalable Android systems, realtime architectures, and production-style engineering.

---

# Portfolio

🌐 https://vlabs99.github.io/VLabs/

---

# License

This project is developed for educational, portfolio, and learning purposes.
