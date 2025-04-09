#include "shamir.h"
#include <android/log.h>
#include <random>
#include <unordered_map>
#include <algorithm>
#include <stdexcept>

// Define log tag
#define LOG_TAG "Shamir_Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace shamir {

// Simple random number generator
std::vector<uint8_t> generateRandomBytes(size_t length) {
    std::vector<uint8_t> bytes(length);
    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<> dis(0, 255);
    
    for (size_t i = 0; i < length; i++) {
        bytes[i] = static_cast<uint8_t>(dis(gen));
    }
    
    return bytes;
}

// Field operations in GF(256)
namespace field {
    // Multiplication in GF(256) using the irreducible polynomial x^8 + x^4 + x^3 + x + 1
    uint8_t mul(uint8_t a, uint8_t b) {
        uint8_t p = 0;
        uint8_t high_bit;
        
        for (int i = 0; i < 8; i++) {
            if (b & 1) {
                p ^= a;
            }
            
            high_bit = (a & 0x80);
            a <<= 1;
            
            if (high_bit) {
                a ^= 0x1B; // The irreducible polynomial
            }
            
            b >>= 1;
        }
        
        return p;
    }
    
    // Division in GF(256)
    uint8_t div(uint8_t a, uint8_t b) {
        if (b == 0) {
            throw std::invalid_argument("Division by zero");
        }
        
        // Find the multiplicative inverse of b
        uint8_t b_inv = 1;
        for (int i = 0; i < 254; i++) { // 254 is the order of the multiplicative group
            b_inv = mul(b_inv, b);
        }
        
        return mul(a, b_inv);
    }
}

// Evaluate a polynomial at a given point
uint8_t evaluatePolynomial(const std::vector<uint8_t>& coefficients, uint8_t x) {
    uint8_t result = 0;
    uint8_t x_pow = 1;
    
    for (uint8_t coeff : coefficients) {
        result ^= field::mul(coeff, x_pow);
        x_pow = field::mul(x_pow, x);
    }
    
    return result;
}

// Lagrange interpolation
uint8_t interpolate(const std::vector<uint8_t>& x_values, 
                   const std::vector<uint8_t>& y_values, 
                   uint8_t x) {
    uint8_t result = 0;
    
    for (size_t i = 0; i < x_values.size(); i++) {
        uint8_t term = y_values[i];
        
        for (size_t j = 0; j < x_values.size(); j++) {
            if (i != j) {
                uint8_t numerator = x ^ x_values[j];
                uint8_t denominator = x_values[i] ^ x_values[j];
                term = field::mul(term, field::div(numerator, denominator));
            }
        }
        
        result ^= term;
    }
    
    return result;
}

// Split a secret into shares
std::vector<std::vector<uint8_t>> splitSecret(const std::vector<uint8_t>& secret, 
                                            int totalShares, 
                                            int threshold) {
    LOGI("Splitting secret of size %zu into %d shares with threshold %d", 
         secret.size(), totalShares, threshold);
    
    // Validate parameters
    if (threshold > totalShares) {
        LOGE("Threshold cannot be greater than total shares");
        throw std::invalid_argument("Threshold cannot be greater than total shares");
    }
    if (threshold < 2) {
        LOGE("Threshold must be at least 2");
        throw std::invalid_argument("Threshold must be at least 2");
    }
    if (totalShares > 255) {
        LOGE("Total shares cannot exceed 255");
        throw std::invalid_argument("Total shares cannot exceed 255");
    }
    
    std::vector<std::vector<uint8_t>> shares(totalShares);
    
    // Process each byte of the secret separately
    for (size_t byteIndex = 0; byteIndex < secret.size(); byteIndex++) {
        // Generate random coefficients for the polynomial
        std::vector<uint8_t> coefficients(threshold);
        coefficients[0] = secret[byteIndex]; // The constant term is the secret byte
        
        // Generate random coefficients for higher-degree terms
        for (int i = 1; i < threshold; i++) {
            coefficients[i] = generateRandomBytes(1)[0];
        }
        
        // Generate shares for this byte
        for (int shareIndex = 0; shareIndex < totalShares; shareIndex++) {
            uint8_t x = shareIndex + 1; // x values start from 1
            uint8_t y = evaluatePolynomial(coefficients, x);
            
            // Initialize the share if this is the first byte
            if (byteIndex == 0) {
                shares[shareIndex].resize(secret.size() + 1); // +1 for the x value
                shares[shareIndex][0] = x; // Store the x value as the first byte
            }
            
            // Store the y value for this byte
            shares[shareIndex][byteIndex + 1] = y;
        }
    }
    
    return shares;
}

// Recover a secret from shares
std::vector<uint8_t> recoverSecret(const std::vector<int>& shareIds, 
                                  const std::vector<std::vector<uint8_t>>& shares) {
    LOGI("Recovering secret from %zu shares", shares.size());
    
    // Validate parameters
    if (shares.size() < 2) {
        LOGE("At least 2 shares are required");
        throw std::invalid_argument("At least 2 shares are required");
    }
    
    // Determine the secret size (share size - 1 for the x value)
    size_t secretSize = shares[0].size() - 1;
    
    // Prepare x and y values for interpolation
    std::vector<uint8_t> x_values(shares.size());
    std::vector<std::vector<uint8_t>> y_values(secretSize, std::vector<uint8_t>(shares.size()));
    
    for (size_t i = 0; i < shares.size(); i++) {
        if (shares[i].size() != secretSize + 1) {
            LOGE("Share %zu has invalid size", i);
            throw std::invalid_argument("Shares have inconsistent sizes");
        }
        
        x_values[i] = static_cast<uint8_t>(shareIds[i]);
        
        for (size_t byteIndex = 0; byteIndex < secretSize; byteIndex++) {
            y_values[byteIndex][i] = shares[i][byteIndex + 1];
        }
    }
    
    // Recover each byte of the secret
    std::vector<uint8_t> secret(secretSize);
    for (size_t byteIndex = 0; byteIndex < secretSize; byteIndex++) {
        secret[byteIndex] = interpolate(x_values, y_values[byteIndex], 0);
    }
    
    return secret;
}

} // namespace shamir
