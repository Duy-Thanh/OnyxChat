package com.nekkochan.onyxchat.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Ignore;

import java.util.Objects;

/**
 * Entity representing a contact in the OnyxChat application.
 */
@Entity(tableName = "contacts")
public class Contact {
    @PrimaryKey
    @NonNull
    private String onionAddress;
    
    private String nickname;
    private boolean isVerified;
    private boolean isBlocked;
    private String publicKey;
    private long lastActiveTimestamp;
    private int unreadCount;

    /**
     * Default constructor required by Room
     */
    public Contact() {
    }

    /**
     * Constructor for creating a new contact
     * 
     * @param onionAddress The contact's Tor onion address
     * @param nickname The contact's display name
     * @param publicKey The contact's public key for encryption
     */
    @Ignore
    public Contact(@NonNull String onionAddress, String nickname, String publicKey) {
        this.onionAddress = onionAddress;
        this.nickname = nickname;
        this.publicKey = publicKey;
        this.isVerified = false;
        this.isBlocked = false;
        this.lastActiveTimestamp = System.currentTimeMillis();
        this.unreadCount = 0;
    }

    @NonNull
    public String getOnionAddress() {
        return onionAddress;
    }

    public void setOnionAddress(@NonNull String onionAddress) {
        this.onionAddress = onionAddress;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public boolean isVerified() {
        return isVerified;
    }

    public void setVerified(boolean verified) {
        isVerified = verified;
    }

    public boolean isBlocked() {
        return isBlocked;
    }

    public void setBlocked(boolean blocked) {
        isBlocked = blocked;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public long getLastActiveTimestamp() {
        return lastActiveTimestamp;
    }

    public void setLastActiveTimestamp(long lastActiveTimestamp) {
        this.lastActiveTimestamp = lastActiveTimestamp;
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
    }

    public void incrementUnreadCount() {
        this.unreadCount++;
    }

    public void clearUnreadCount() {
        this.unreadCount = 0;
    }

    /**
     * Get display name - returns nickname if available, otherwise returns a shortened onion address
     * 
     * @return The contact's display name
     */
    public String getDisplayName() {
        if (nickname != null && !nickname.isEmpty()) {
            return nickname;
        } else {
            // Return shortened onion address if no nickname
            return shortenOnionAddress(onionAddress);
        }
    }
    
    /**
     * Shorten an onion address for display purposes
     * 
     * @param address The full onion address
     * @return A shortened version (first 6 chars + ... + last 6 chars)
     */
    public static String shortenOnionAddress(String address) {
        if (address == null || address.length() <= 15) {
            return address;
        }
        return address.substring(0, 6) + "..." + address.substring(address.length() - 6);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Contact contact = (Contact) o;
        return Objects.equals(onionAddress, contact.onionAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(onionAddress);
    }
}