package com.vchat.app;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import com.google.firebase.FirebaseApp;
import com.vchat.app.notifications.MyFirebaseMessagingService;

public class VChatApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseApp.initializeApp(this);
        createNotificationChannels();
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;

        NotificationChannel requestChannel = new NotificationChannel(
                MyFirebaseMessagingService.CHANNEL_REQUESTS,
                "Chat Requests",
                NotificationManager.IMPORTANCE_HIGH
        );
        requestChannel.setDescription("Incoming chat request notifications");

        NotificationChannel chatChannel = new NotificationChannel(
                MyFirebaseMessagingService.CHANNEL_CHATS,
                "Chats",
                NotificationManager.IMPORTANCE_HIGH
        );
        chatChannel.setDescription("Chat updates and accepted request notifications");

        manager.createNotificationChannel(requestChannel);
        manager.createNotificationChannel(chatChannel);
    }
}
