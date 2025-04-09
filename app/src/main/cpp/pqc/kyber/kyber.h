#pragma once

#include <vector>
#include <cstdint>

namespace kyber {

// Kyber variants
enum class Variant {
    KYBER_512 = 1,  // Level 1 security (AES-128 equivalent)
    KYBER_768 = 2,  // Level 3 security (AES-192 equivalent)
    KYBER_1024 = 3  // Level 5 security (AES-256 equivalent)
};

// Key pair structure
struct KeyPair {
    std::vector<uint8_t> publicKey;
    std::vector<uint8_t> privateKey;
};

// Encapsulation result structure
struct EncapsulationResult {
    std::vector<uint8_t> ciphertext;
    std::vector<uint8_t> sharedSecret;
};

// Initialize Kyber module
void initialize();

// Generate a Kyber key pair
KeyPair generateKeyPair(Variant variant);

// Encapsulate a shared secret using a public key
EncapsulationResult encapsulate(const std::vector<uint8_t>& publicKey);

// Decapsulate a shared secret using a private key and ciphertext
std::vector<uint8_t> decapsulate(const std::vector<uint8_t>& privateKey, 
                                const std::vector<uint8_t>& ciphertext);

} // namespace kyber
