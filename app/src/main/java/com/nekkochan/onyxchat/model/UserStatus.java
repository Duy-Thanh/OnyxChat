package com.nekkochan.onyxchat.model;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Model class to represent a user's online status and last active time
 */
public class UserStatus {
    private final boolean isOnline;
    private final String lastActiveAt;
    
    private static final SimpleDateFormat ISO_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
    
    static {
        ISO_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }
    
    public UserStatus(boolean isOnline, String lastActiveAt) {
        this.isOnline = isOnline;
        this.lastActiveAt = lastActiveAt;
    }
    
    public boolean isOnline() {
        return isOnline;
    }
    
    public String getLastActiveAt() {
        return lastActiveAt;
    }
    
    /**
     * Get a formatted string showing when the user was last active
     * @return "Online" if user is online, otherwise "Online X time ago"
     */
    public String getFormattedStatus() {
        if (isOnline) {
            return "Online";
        } else if (lastActiveAt != null) {
            try {
                Date lastActive = ISO_FORMAT.parse(lastActiveAt);
                if (lastActive != null) {
                    return "Online " + getTimeAgo(lastActive);
                }
            } catch (ParseException e) {
                // If parsing fails, just return a default
            }
        }
        return "Offline";
    }
    
    private String getTimeAgo(Date lastActive) {
        Date now = new Date();
        long diffInMillis = now.getTime() - lastActive.getTime();
        
        // Convert to appropriate time unit
        if (diffInMillis < TimeUnit.MINUTES.toMillis(1)) {
            return "just now";
        } else if (diffInMillis < TimeUnit.HOURS.toMillis(1)) {
            long minutes = TimeUnit.MILLISECONDS.toMinutes(diffInMillis);
            return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        } else if (diffInMillis < TimeUnit.DAYS.toMillis(1)) {
            long hours = TimeUnit.MILLISECONDS.toHours(diffInMillis);
            return hours + (hours == 1 ? " hour ago" : " hours ago");
        } else if (diffInMillis < TimeUnit.DAYS.toMillis(7)) {
            long days = TimeUnit.MILLISECONDS.toDays(diffInMillis);
            return days + (days == 1 ? " day ago" : " days ago");
        } else {
            // For longer periods, show the actual date
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d", Locale.getDefault());
            return "on " + dateFormat.format(lastActive);
        }
    }
} 