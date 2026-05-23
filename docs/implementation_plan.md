# Goal Description

The goal is to develop VChat, a native Android real-time messaging application from scratch, fulfilling all requirements outlined in the provided Product Requirements Document (PRD). The application will support 1-on-1 and group chats, media sharing, user authentication, and real-time syncing using Firebase services.

## User Review Required

> [!IMPORTANT]
> Since this is a native Android application built with Java, it requires a standard Android Gradle project structure. I can generate the basic Android project structure (Gradle files, Manifest, resource directories, and core Java packages) manually, step-by-step. Please confirm if you want me to write out these configuration and core files from scratch in the upcoming phases, or if you already have an empty Android Studio project initialized in this directory.

## Open Questions

1. **Empty Project vs Generating from Scratch:** Do you want me to write the `build.gradle`, `AndroidManifest.xml`, and baseline resources from scratch, or do you want to initialize a blank project in Android Studio yourself and have me start creating the packages/activities inside it? (I recommend you initialize an "Empty Views Activity" project in Android Studio first, as it sets up the Gradle wrapper and SDK paths perfectly for your local environment).
2. **Package Name:** What package name should we use? (e.g., `com.vchat.app`)
3. **Firebase Setup:** I will create the structural code for Firebase, but you will need to add the `google-services.json` file from your Firebase console manually. Does that sound good?

## Proposed Changes

### Project Summary
VChat is a real-time messaging Android application allowing users to communicate via personal text messaging, group chats, and media sharing. It is intended for public APK distribution and portfolio showcase. Key features include user authentication, push notifications, read receipts, and online status, avoiding audio/video calls or payment integrations to focus on core messaging performance.

### Feature Breakdown
1. **Authentication:** Register, login, logout, password reset via Firebase Auth.
2. **User Profiles:** Customizable profiles (image, username, bio, online/last seen status).
3. **Core Navigation:** Splash, Home (Chats, Groups, Search, Settings).
4. **Search System:** Discover users by username or email.
5. **Personal Chat:** Real-time text messaging, message status (sent/delivered/seen), chat history.
6. **Group Chat:** Create groups, add/remove members, group messaging, admin controls.
7. **Media Sharing:** Send/receive images, documents, and emojis via Firebase Storage.
8. **Notifications:** FCM-based push notifications for new and group messages.
9. **Status Indicators:** Online status, last seen, typing indicators.
10. **Settings:** Dark mode, password changes, notification and privacy toggles.

### Tech Stack Recommendation
- **Platform:** Android (Native)
- **Language:** Java (as requested) & XML for Layouts
- **Architecture:** MVVM (Model-View-ViewModel) pattern to ensure scalable separation of UI and business logic.
- **Backend/Database:** Firebase Firestore (NoSQL Document database for real-time sync)
- **Authentication:** Firebase Authentication (Email/Password)
- **Storage:** Firebase Cloud Storage
- **Push Notifications:** Firebase Cloud Messaging (FCM)
- **Image Loading:** Glide (Optimized for Android image caching)
- **UI Components:** Material Design Components, RecyclerView, CardView.

### Architecture Diagram Explanation
The system follows a reactive Client-Serverless architecture:
- **Client Tier (Android App):** 
  - **UI Layer (Activities/Fragments):** Observes data and displays it.
  - **Presentation Layer (ViewModels):** Manages UI state and business logic.
  - **Data Layer (Repositories):** Interacts with Firebase SDKs.
- **Backend Tier (Firebase):**
  - **FirebaseAuth:** Validates credentials and manages sessions.
  - **Firestore:** Stores denormalized NoSQL data (Users, Chats, Groups, Messages) and pushes updates to the client via Snapshot Listeners instantly.
  - **Firebase Storage:** Handles uploads of media and profile pictures, returning URLs to be stored in Firestore.
  - **FCM:** Listens to database triggers (via Cloud Functions) or client-to-client payloads to deliver push notifications when the app is backgrounded.

### Folder Structure
The project will be organized using feature-based and layer-based modularization:
```text
app/src/main/
├── AndroidManifest.xml
├── java/com/vchat/app/
│   ├── activities/      (Splash, Login, Main, Chat Activities)
│   ├── fragments/       (Chats, Groups, Profile Fragments for BottomNav)
│   ├── adapters/        (RecyclerView Adapters for messages, users, etc.)
│   ├── models/          (User, Message, Group, Notification POJOs)
│   ├── viewmodels/      (MVVM ViewModels)
│   ├── repositories/    (Firebase logic abstraction)
│   ├── utils/           (Constants, Helpers, PreferenceManagers)
│   └── notifications/   (FCM Messaging Services)
└── res/
    ├── layout/          (XML layouts)
    ├── drawable/        (Icons, backgrounds)
    ├── values/          (colors.xml, strings.xml, themes.xml)
    └── xml/             (Network security configs, file paths)
```

## Verification Plan
1. **Initial Setup:** Verify that the Gradle syncs successfully and the base app runs on an emulator/device.
2. **Auth Flow:** Test registration and login, ensuring users are created in Firebase Auth and Firestore.
3. **Messaging Flow:** Use two test devices/emulators to verify real-time personal and group messaging.
4. **Media Verification:** Test uploading an image and validating its presence in Firebase Storage and appearance in the chat UI.
