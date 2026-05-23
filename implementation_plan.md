# Phase 2: Core UI and Navigation

This plan outlines the steps to implement the core user interface, navigation system, and the user directory for VChat.

## User Review Required
Please review the proposed bottom navigation structure. The app will feature a standard bottom navigation bar with four tabs: Chats, Groups, Users (Search), and Profile.

## Proposed Changes

### Resources & Layouts
- **Menu**: Create `res/menu/bottom_nav_menu.xml` for the Bottom Navigation Bar.
- **Drawables**: Add modern vector icons for the four tabs and an item background.
- **Layouts**: 
  - Update `activity_main.xml` to include `FragmentContainerView` and `BottomNavigationView`.
  - Create `fragment_chats.xml`, `fragment_groups.xml`, `fragment_users.xml`, `fragment_profile.xml`.
  - Create `item_user.xml` for displaying a single user in the search screen.

### Navigation & Fragments
- **MainActivity**: Implement bottom navigation logic to seamlessly switch between fragments.
- **Fragments**: Implement the Java classes for `ChatsFragment`, `GroupsFragment`, `UsersFragment`, and `ProfileFragment`.

### User Search System
- **Adapter**: Create `UsersAdapter` to populate the `RecyclerView` in the Users screen.
- **Firestore**: Implement a real-time listener or query in `UsersFragment` to fetch and display registered users from the `users` Firestore collection.

## Verification Plan

### Automated Tests
- Build the app and verify there are no compile-time errors.

### Manual Verification
- Deploy the app to an emulator or physical device.
- Verify the Bottom Navigation Bar displays correctly and switches fragments without crashing.
- Navigate to the "Users" tab and verify it successfully fetches and displays the list of users from Firestore.
