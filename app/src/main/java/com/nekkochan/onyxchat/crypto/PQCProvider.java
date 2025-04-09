package com.nekkochan.onyxchat.crypto;

import android.util.Base64;
import android.util.Log;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;

/**
 * Native implementation of Post-Quantum Cryptography operations using JNI.
 * This class provides an interface to the C++ implementation of Kyber and Dilithium algorithms.
 */
public class PQCProvider {
    private static final String TAG = "PQCProvider";

    // Constants for the types of algorithms
    public static final String ALGORITHM_KYBER = "KYBER";
    public static final String ALGORITHM_DILITHIUM = "DILITHIUM";
    
    // Algorithm variants
    public static final int KYBER_512 = 1;
    public static final int KYBER_768 = 2;
    public static final int KYBER_1024 = 3;
    
    public static final int DILITHIUM_2 = 1;
    public static final int DILITHIUM_3 = 2;
    public static final int DILITHIUM_5 = 3;

    // Default algorithm and variant
    private static int kyberVariant = KYBER_768;
    private static int dilithiumVariant = DILITHIUM_3;
    
    // Load the native library
    static {
        try {
            System.loadLibrary("pqc-native");
            Log.d(TAG, "PQC native library loaded successfully");
            nativeInitialize();
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load PQC native library", e);
        }
    }
    
    /**
     * Initialize the native library
     */
    private static native void nativeInitialize();
    
    /**
     * Set the Kyber variant to use
     * @param variant KYBER_512, KYBER_768, or KYBER_1024
     */
    public static void setKyberVariant(int variant) {
        if (variant < KYBER_512 || variant > KYBER_1024) {
            throw new IllegalArgumentException("Invalid Kyber variant");
        }
        kyberVariant = variant;
    }
    
    /**
     * Set the Dilithium variant to use
     * @param variant DILITHIUM_2, DILITHIUM_3, or DILITHIUM_5
     */
    public static void setDilithiumVariant(int variant) {
        if (variant < DILITHIUM_2 || variant > DILITHIUM_5) {
            throw new IllegalArgumentException("Invalid Dilithium variant");
        }
        dilithiumVariant = variant;
    }

    /**
     * Generate a Kyber key pair
     * @return KyberKeyPair containing public and private keys
     */
    public static KyberKeyPair generateKyberKeyPair() {
        try {
            byte[][] keyPairArray = nativeGenerateKyberKeyPair(kyberVariant);
            if (keyPairArray != null && keyPairArray.length == 2) {
                return new KyberKeyPair(keyPairArray[0], keyPairArray[1]);
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error generating Kyber key pair", e);
            return null;
        }
    }
    
    /**
     * Generate a Dilithium key pair for digital signatures
     * @return DilithiumKeyPair containing public and private keys
     */
    public static DilithiumKeyPair generateDilithiumKeyPair() {
        try {
            byte[][] keyPairArray = nativeGenerateDilithiumKeyPair(dilithiumVariant);
            if (keyPairArray != null && keyPairArray.length == 2) {
                return new DilithiumKeyPair(keyPairArray[0], keyPairArray[1]);
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error generating Dilithium key pair", e);
            return null;
        }
    }

    /**
     * Encapsulate a key with a Kyber public key
     * @param publicKey The Kyber public key to use for encapsulation
     * @return KyberEncapsulationResult containing the ciphertext and shared secret
     */
    public static KyberEncapsulationResult encapsulateKey(byte[] publicKey) {
        try {
            byte[][] resultArray = nativeEncapsulateKey(publicKey);
            if (resultArray != null && resultArray.length == 2) {
                return new KyberEncapsulationResult(resultArray[0], resultArray[1]);
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error encapsulating key", e);
            return null;
        }
    }

    /**
     * Decapsulate a key with a Kyber private key
     * @param privateKey The Kyber private key to use for decapsulation
     * @param ciphertext The ciphertext to decapsulate
     * @return The shared secret key
     */
    public static byte[] decapsulateKey(byte[] privateKey, byte[] ciphertext) {
        try {
            return nativeDecapsulateKey(privateKey, ciphertext);
        } catch (Exception e) {
            Log.e(TAG, "Error decapsulating key", e);
            return null;
        }
    }
    
    /**
     * Sign a message using Dilithium
     * @param privateKey The Dilithium private key
     * @param message The message to sign
     * @return The signature
     */
    public static byte[] sign(byte[] privateKey, byte[] message) {
        try {
            return nativeSignMessage(privateKey, message);
        } catch (Exception e) {
            Log.e(TAG, "Error signing message", e);
            return null;
        }
    }
    
    /**
     * Verify a signature using Dilithium
     * @param publicKey The Dilithium public key
     * @param message The message that was signed
     * @param signature The signature to verify
     * @return true if the signature is valid, false otherwise
     */
    public static boolean verify(byte[] publicKey, byte[] message, byte[] signature) {
        try {
            return nativeVerifySignature(publicKey, message, signature);
        } catch (Exception e) {
            Log.e(TAG, "Error verifying signature", e);
            return false;
        }
    }
    
    /**
     * Encrypt data with AES-GCM using the provided key
     * @param plaintext The plaintext to encrypt
     * @param key The key to use
     * @return EncryptedData containing the IV and ciphertext
     */
    public static EncryptedData encryptWithAES(byte[] plaintext, byte[] key) {
        try {
            byte[][] resultArray = nativeEncrypt(plaintext, key);
            if (resultArray != null && resultArray.length == 2) {
                return new EncryptedData(resultArray[0], resultArray[1]);
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error encrypting data", e);
            return null;
        }
    }
    
    /**
     * Encrypt a string with AES-GCM using the provided key
     * @param plaintext The plaintext string to encrypt
     * @param key The key to use
     * @return EncryptedData containing the IV and ciphertext
     */
    public static EncryptedData encryptWithAES(String plaintext, byte[] key) {
        return encryptWithAES(plaintext.getBytes(StandardCharsets.UTF_8), key);
    }

    /**
     * Decrypt data with AES-GCM
     * @param encryptedData The encrypted data to decrypt
     * @param key The key to use
     * @return The decrypted plaintext
     */
    public static byte[] decryptWithAES(EncryptedData encryptedData, byte[] key) {
        try {
            return nativeDecrypt(
                Base64.decode(encryptedData.getIv(), Base64.NO_WRAP),
                Base64.decode(encryptedData.getCiphertext(), Base64.NO_WRAP),
                key
            );
        } catch (Exception e) {
            Log.e(TAG, "Error decrypting data", e);
            return null;
        }
    }
    
    /**
     * Decrypt data with AES-GCM and return as a string
     * @param encryptedData The encrypted data to decrypt
     * @param key The key to use
     * @return The decrypted plaintext as a string
     */
    public static String decryptWithAESAsString(EncryptedData encryptedData, byte[] key) {
        byte[] decrypted = decryptWithAES(encryptedData, key);
        if (decrypted == null) {
            return null;
        }
        return new String(decrypted, StandardCharsets.UTF_8);
    }
    
    /**
     * Encode a public key as a Base64 string
     * @param publicKey The public key to encode
     * @return The encoded public key
     */
    public static String encodePublicKey(byte[] publicKey) {
        return Base64.encodeToString(publicKey, Base64.NO_WRAP);
    }
    
    /**
     * Encode a private key as a Base64 string
     * @param privateKey The private key to encode
     * @return The encoded private key
     */
    public static String encodePrivateKey(byte[] privateKey) {
        return Base64.encodeToString(privateKey, Base64.NO_WRAP);
    }
    
    /**
     * Decode a Base64 encoded public key
     * @param encodedKey The encoded public key
     * @return The decoded public key
     */
    public static byte[] decodePublicKey(String encodedKey) {
        return Base64.decode(encodedKey, Base64.NO_WRAP);
    }
    
    /**
     * Decode a Base64 encoded private key
     * @param encodedKey The encoded private key
     * @return The decoded private key
     */
    public static byte[] decodePrivateKey(String encodedKey) {
        return Base64.decode(encodedKey, Base64.NO_WRAP);
    }
    
    // Native method declarations
    private static native byte[][] nativeGenerateKyberKeyPair(int variant);
    private static native byte[][] nativeGenerateDilithiumKeyPair(int variant);
    private static native byte[][] nativeEncapsulateKey(byte[] publicKey);
    private static native byte[] nativeDecapsulateKey(byte[] privateKey, byte[] ciphertext);
    private static native byte[] nativeSignMessage(byte[] privateKey, byte[] message);
    private static native boolean nativeVerifySignature(byte[] publicKey, byte[] message, byte[] signature);
    private static native byte[][] nativeEncrypt(byte[] plaintext, byte[] key);
    private static native byte[] nativeDecrypt(byte[] iv, byte[] ciphertext, byte[] key);

    /**
     * Class representing a Kyber key pair
     */
    public static class KyberKeyPair {
        private final byte[] publicKey;
        private final byte[] privateKey;

        public KyberKeyPair(byte[] publicKey, byte[] privateKey) {
            this.publicKey = publicKey;
            this.privateKey = privateKey;
        }

        public byte[] getPublicKey() {
            return publicKey;
        }

        public byte[] getPrivateKey() {
            return privateKey;
        }
        
        public String getEncodedPublicKey() {
            return Base64.encodeToString(publicKey, Base64.NO_WRAP);
        }
        
        public String getEncodedPrivateKey() {
            return Base64.encodeToString(privateKey, Base64.NO_WRAP);
        }
    }
    
    /**
     * Class representing a Dilithium key pair
     */
    public static class DilithiumKeyPair {
        private final byte[] publicKey;
        private final byte[] privateKey;

        public DilithiumKeyPair(byte[] publicKey, byte[] privateKey) {
            this.publicKey = publicKey;
            this.privateKey = privateKey;
        }

        public byte[] getPublicKey() {
            return publicKey;
        }

        public byte[] getPrivateKey() {
            return privateKey;
        }
        
        public String getEncodedPublicKey() {
            return Base64.encodeToString(publicKey, Base64.NO_WRAP);
        }
        
        public String getEncodedPrivateKey() {
            return Base64.encodeToString(privateKey, Base64.NO_WRAP);
        }
    }
    
    /**
     * Class representing a Kyber encapsulation result
     */
    public static class KyberEncapsulationResult {
        private final byte[] ciphertext;
        private final byte[] sharedSecret;

        public KyberEncapsulationResult(byte[] ciphertext, byte[] sharedSecret) {
            this.ciphertext = ciphertext;
            this.sharedSecret = sharedSecret;
        }

        public byte[] getCiphertext() {
            return ciphertext;
        }

        public byte[] getSharedSecret() {
            return sharedSecret;
        }
        
        public String getEncodedCiphertext() {
            return Base64.encodeToString(ciphertext, Base64.NO_WRAP);
        }
    }

    /**
     * Class representing encrypted data
     */
    public static class EncryptedData {
        private final String iv;
        private final String ciphertext;

        public EncryptedData(String iv, String ciphertext) {
            this.iv = iv;
            this.ciphertext = ciphertext;
        }
        
        public EncryptedData(byte[] iv, byte[] ciphertext) {
            this.iv = Base64.encodeToString(iv, Base64.NO_WRAP);
            this.ciphertext = Base64.encodeToString(ciphertext, Base64.NO_WRAP);
        }

        public String getIv() {
            return iv;
        }

        public String getCiphertext() {
            return ciphertext;
        }
    }
}