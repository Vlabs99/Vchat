package com.vchat.app.chat;

import android.text.TextUtils;

import com.vchat.app.models.MessageModel;

import java.util.List;

public class ChatSelectionController {
    public String joinMessageTexts(List<MessageModel> messages) {
        if (messages == null || messages.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < messages.size(); i++) {
            String text = messages.get(i).getMessageText();
            if (!TextUtils.isEmpty(text)) sb.append(text);
            if (i < messages.size() - 1) sb.append("\n");
        }
        return sb.toString();
    }
}
