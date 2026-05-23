package com.vchat.app.chat;

public class ChatMessagePaginator {
    public boolean shouldLoadOlder(int firstVisiblePosition, boolean isLoading, boolean hasMoreOlder) {
        return !isLoading && hasMoreOlder && firstVisiblePosition <= 4;
    }
}
