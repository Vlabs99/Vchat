package com.vchat.app.chat;

import android.text.TextUtils;

public class ChatPermissionHelper {

    public static boolean isDirectChatRestricted(
            boolean isGroup,
            boolean canSendDirectMessage,
            String restrictionState
    ) {

        return !isGroup
                && (
                !canSendDirectMessage
                        || !TextUtils.isEmpty(restrictionState)
        );
    }

    public static boolean allowComposerMessaging(
            boolean isGroup,
            boolean canSendDirectMessage,
            String restrictionState
    ) {

        return !isDirectChatRestricted(
                isGroup,
                canSendDirectMessage,
                restrictionState
        );
    }
}