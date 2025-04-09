package com.nekkochan.onyxchat.crypto;

import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

/**
 * Native implementation of Shamir's Secret Sharing scheme for key recovery using JNI.
 * This allows splitting a secret into multiple shares and reconstructing
 * the original secret with a threshold number of shares.
 */
public class SecretSharing {
    private static final String TAG = "SecretSharing";
    
    // Load the native library
    static {
        try {
            // The PQCProvider already loads the library, but we check here as well
            // in case this class is used independently
            System.loadLibrary("pqc-native");
            Log.d(TAG, "PQC native library loaded successfully for SecretSharing");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load PQC native library", e);
        }
    }
    
    /**
     * Split a secret into n shares, requiring k shares to reconstruct
     * @param secret The secret to split
     * @param totalShares Total number of shares to generate
     * @param threshold Minimum number of shares required to reconstruct
     * @return Map of share IDs to Base64-encoded share data
     */
    public static Map<Integer, String> splitSecret(String secret, int totalShares, int threshold) {
        try {
            // Validate parameters
            if (threshold > totalShares) {
                throw new IllegalArgumentException("Threshold cannot be greater than total shares");
            }
            if (threshold < 2) {
                throw new IllegalArgumentException("Threshold must be at least 2");
            }
            if (totalShares > 255) {
                throw new IllegalArgumentException("Total shares cannot exceed 255");
            }
            
            // Convert secret to bytes
            byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
            
            // Call native implementation
            byte[][] shares = nativeSplitSecret(secretBytes, totalShares, threshold);
            
            // Convert shares to Base64 strings for easier storage
            Map<Integer, String> encodedShares = new HashMap<>();
            for (int i = 0; i < shares.length; i++) {
                String encodedShare = Base64.encodeToString(shares[i], Base64.NO_WRAP);
                encodedShares.put(i + 1, encodedShare); // Share IDs start from 1
            }
            
            return encodedShares;
        } catch (Exception e) {
            Log.e(TAG, "Error splitting secret: " + e.getMessage());
            throw new RuntimeException("Failed to split secret", e);
        }
    }
    
    /**
     * Recover a secret from a set of shares
     * @param shares Map of share IDs to Base64-encoded share data
     * @return The reconstructed secret
     */
    public static String recoverSecret(Map<Integer, String> encodedShares) {
        try {
            // Validate parameters
            if (encodedShares.size() < 2) {
                throw new IllegalArgumentException("At least 2 shares are required to recover the secret");
            }
            
            // Convert Base64 encoded shares back to bytes
            int[] shareIds = new int[encodedShares.size()];
            byte[][] shareData = new byte[encodedShares.size()][];
            
            int index = 0;
            for (Map.Entry<Integer, String> entry : encodedShares.entrySet()) {
                shareIds[index] = entry.getKey();
                shareData[index] = Base64.decode(entry.getValue(), Base64.NO_WRAP);
                index++;
            }
            
            // Call native implementation
            byte[] recoveredSecretBytes = nativeRecoverSecret(shareIds, shareData);
            
            // Convert recovered bytes back to string
            return new String(recoveredSecretBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "Error recovering secret: " + e.getMessage());
            throw new RuntimeException("Failed to recover secret", e);
        }
    }
    
    // Native method declarations
    private static native byte[][] nativeSplitSecret(byte[] secret, int totalShares, int threshold);
    private static native byte[] nativeRecoverSecret(int[] shareIds, byte[][] shares);
}