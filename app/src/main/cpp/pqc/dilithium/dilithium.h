#pragma once

#include <vector>
#include <cstdint>

namespace dilithium {

// Dilithium variants
enum class Variant {
    DILITHIUM_2 = 1,  // Level 2 security
    DILITHIUM_3 = 2,  // Level 3 security
    DILITHIUM_5 = 3   // Level 5 security
};

// Key pair structure
struct KeyPair {
    std::vector<uint8_t> publicKey;
    std::vector<uint8_t> privateKey;
};

// Initialize Dilithium module
void initialize();

// Generate a Dilithium key pair
KeyPair generateKeyPair(Variant variant);

// Sign a message using a private key
std::vector<uint8_t> sign(const std::vector<uint8_t>& privateKey, 
                         const std::vector<uint8_t>& message);

// Verify a signature using a public key
bool verify(const std::vector<uint8_t>& publicKey, 
           const std::vector<uint8_t>& message, 
           const std::vector<uint8_t>& signature);

} // namespace dilithium
