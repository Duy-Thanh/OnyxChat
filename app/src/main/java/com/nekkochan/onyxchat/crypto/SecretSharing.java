package com.nekkochan.onyxchat.crypto;

import android.util.Base64;
import android.util.Log;

import com.codahale.shamir.Scheme;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

/**
 * Implements Shamir's Secret Sharing scheme for key recovery
 * This allows splitting a secret into multiple shares and reconstructing
 * the original secret with a threshold number of shares
 */
public class SecretSharing {
    private static final String TAG = "SecretSharing";
    
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
            
            // Create a secure random number generator
            SecureRandom random = new SecureRandom();
            
            // Create the scheme
            Scheme scheme = new Scheme(random, totalShares, threshold);
            
            // Split the secret
            Map<Integer, byte[]> shares = scheme.split(secretBytes);
            
            // Convert shares to Base64 strings for easier storage
            Map<Integer, String> encodedShares = new HashMap<>();
            for (Map.Entry<Integer, byte[]> entry : shares.entrySet()) {
                String encodedShare = Base64.encodeToString(entry.getValue(), Base64.NO_WRAP);
                encodedShares.put(entry.getKey(), encodedShare);
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
     * @param threshold Minimum number of shares required to reconstruct
     * @return The reconstructed secret
     */
    public static String recoverSecret(Map<Integer, String> encodedShares, int threshold) {
        try {
            // Validate parameters
            if (encodedShares.size() < threshold) {
                throw new IllegalArgumentException("Not enough shares provided (got " + 
                        encodedShares.size() + ", need " + threshold + ")");
            }
            
            // Convert Base64 encoded shares back to bytes
            Map<Integer, byte[]> shares = new HashMap<>();
            for (Map.Entry<Integer, String> entry : encodedShares.entrySet()) {
                byte[] shareData = Base64.decode(entry.getValue(), Base64.NO_WRAP);
                shares.put(entry.getKey(), shareData);
            }
            
            // Create the scheme with the provided threshold
            Scheme scheme = new Scheme(new SecureRandom(), 255, threshold);
            
            // Recover the secret
            byte[] recoveredSecretBytes = scheme.join(shares);
            
            // Convert recovered bytes back to string
            return new String(recoveredSecretBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "Error recovering secret: " + e.getMessage());
            throw new RuntimeException("Failed to recover secret", e);
        }
    }
} 