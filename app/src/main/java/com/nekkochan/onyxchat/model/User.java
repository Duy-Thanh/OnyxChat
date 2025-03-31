package com.nekkochan.onyxchat.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Entity representing the user of the OnyxChat application.
 */
@Entity(tableName = "users")
public class User {
    @PrimaryKey
    @NonNull
    private String id;
    
    private String displayName;
    private String onionAddress;
    private String privateKey;
    private String publicKey;
    private long createdTimestamp;
    private long lastActiveTimestamp;
    private String keyAlgorithm; // e.g., "Kyber", "Dilithium", etc.
    private String status; // User status message

    /**
     * Default constructor required by Room
     */
    public User() {
    }

    /**
     * Constructor for creating a new user
     * 
     * @param id The user's unique identifier
     * @param displayName The user's display name
     * @param onionAddress The user's Tor onion address
     * @param privateKey The user's private key
     * @param publicKey The user's public key
     * @param keyAlgorithm The cryptographic algorithm used for keys
     */
    public User(@NonNull String id, String displayName, String onionAddress, 
                String privateKey, String publicKey, String keyAlgorithm) {
        this.id = id;
        this.displayName = displayName;
        this.onionAddress = onionAddress;
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.keyAlgorithm = keyAlgorithm;
        this.createdTimestamp = System.currentTimeMillis();
        this.lastActiveTimestamp = this.createdTimestamp;
        this.status = "Available"; // Default status
    }

    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getOnionAddress() {
        return onionAddress;
    }

    public void setOnionAddress(String onionAddress) {
        this.onionAddress = onionAddress;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public long getCreatedTimestamp() {
        return createdTimestamp;
    }

    public void setCreatedTimestamp(long createdTimestamp) {
        this.createdTimestamp = createdTimestamp;
    }

    public long getLastActiveTimestamp() {
        return lastActiveTimestamp;
    }

    public void setLastActiveTimestamp(long lastActiveTimestamp) {
        this.lastActiveTimestamp = lastActiveTimestamp;
    }

    public String getKeyAlgorithm() {
        return keyAlgorithm;
    }

    public void setKeyAlgorithm(String keyAlgorithm) {
        this.keyAlgorithm = keyAlgorithm;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Update the last active timestamp to the current time
     */
    public void updateLastActive() {
        this.lastActiveTimestamp = System.currentTimeMillis();
    }

    /**
     * Generate a QR code content for sharing the onion address with contacts
     * 
     * @return String content for QR code
     */
    public String generateQRCodeContent() {
        return "onyxchat:" + onionAddress + ":" + publicKey;
    }
} 