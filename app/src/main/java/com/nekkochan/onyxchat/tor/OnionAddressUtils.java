package com.nekkochan.onyxchat.tor;

import android.text.TextUtils;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for working with Tor onion addresses.
 */
public class OnionAddressUtils {
    // Pattern for V3 onion addresses (56 chars + .onion)
    private static final Pattern V3_ONION_PATTERN = 
            Pattern.compile("^[a-z2-7]{56}\\.onion$", Pattern.CASE_INSENSITIVE);
    
    // Pattern for V2 onion addresses (16 chars + .onion) - legacy but still support it
    private static final Pattern V2_ONION_PATTERN = 
            Pattern.compile("^[a-z2-7]{16}\\.onion$", Pattern.CASE_INSENSITIVE);
    
    /**
     * Validates if a string is a valid Tor onion address.
     * @param address The address to validate
     * @return true if the address is valid
     */
    public static boolean isValidOnionAddress(String address) {
        if (TextUtils.isEmpty(address)) {
            return false;
        }
        
        // Check for V3 onion addresses (current standard)
        Matcher v3Matcher = V3_ONION_PATTERN.matcher(address);
        if (v3Matcher.matches()) {
            return true;
        }
        
        // Also check for V2 onion addresses (legacy)
        Matcher v2Matcher = V2_ONION_PATTERN.matcher(address);
        return v2Matcher.matches();
    }
    
    /**
     * Formats an address to ensure it ends with .onion
     * @param address The address to format
     * @return The formatted address
     */
    public static String formatOnionAddress(String address) {
        if (TextUtils.isEmpty(address)) {
            return address;
        }
        
        // Remove whitespace
        address = address.trim();
        
        // Add .onion if it's missing
        if (!address.endsWith(".onion")) {
            address = address + ".onion";
        }
        
        return address.toLowerCase();
    }
    
    /**
     * Creates a hash identifier for an onion address, useful for storing
     * contacts securely without revealing the full address.
     * 
     * @param onionAddress The onion address to hash
     * @return A Base64 string of the SHA-256 hash of the address
     */
    public static String hashOnionAddress(String onionAddress) {
        if (TextUtils.isEmpty(onionAddress)) {
            return "";
        }
        
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(onionAddress.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(hash, Base64.NO_WRAP);
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }
    
    /**
     * Create a short display version of an onion address for UI.
     * 
     * @param address The full onion address
     * @return A shortened version for display
     */
    public static String getDisplayAddress(String address) {
        if (TextUtils.isEmpty(address)) {
            return "";
        }
        
        if (address.length() > 20) {
            // For v3 addresses, show first 8 chars, ellipsis, last 8 chars
            return address.substring(0, 8) + "..." + 
                    address.substring(address.length() - 13); // includes .onion
        } else {
            // For v2 addresses, show the full address
            return address;
        }
    }
} 