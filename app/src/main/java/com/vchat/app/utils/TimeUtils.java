package com.vchat.app.utils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class TimeUtils {

    public static String getFormattedTime(long timestamp) {
        if (timestamp <= 0) return "";

        Calendar messageTime = Calendar.getInstance();
        messageTime.setTimeInMillis(timestamp);

        Calendar now = Calendar.getInstance();
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DATE, -1);

        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());

        if (isSameDay(now, messageTime)) {
            return timeFormat.format(messageTime.getTime());
        } else if (isSameDay(yesterday, messageTime)) {
            return "Yesterday";
        } else {
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            return dateFormat.format(messageTime.getTime());
        }
    }

    public static String getLastSeenFormatted(long timestamp) {
        if (timestamp <= 0) return "Offline";

        Calendar lastSeenTime = Calendar.getInstance();
        lastSeenTime.setTimeInMillis(timestamp);

        Calendar now = Calendar.getInstance();
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DATE, -1);

        long diffInMillis = now.getTimeInMillis() - timestamp;
        long diffInMinutes = diffInMillis / (60 * 1000);

        if (diffInMinutes < 1) {
            return "Just now";
        }

        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());

        if (isSameDay(now, lastSeenTime)) {
            return "Last seen today at " + timeFormat.format(lastSeenTime.getTime());
        } else if (isSameDay(yesterday, lastSeenTime)) {
            return "Last seen yesterday at " + timeFormat.format(lastSeenTime.getTime());
        } else {
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            return "Last seen " + dateFormat.format(lastSeenTime.getTime());
        }
    }

    private static boolean isSameDay(Calendar c1, Calendar c2) {
        return c1.get(Calendar.DATE) == c2.get(Calendar.DATE)
                && c1.get(Calendar.MONTH) == c2.get(Calendar.MONTH)
                && c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR);
    }
}
