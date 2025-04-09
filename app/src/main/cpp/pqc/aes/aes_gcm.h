#pragma once

#include <vector>
#include <cstdint>

namespace aes {

// Encrypted data structure
struct EncryptedData {
    std::vector<uint8_t> iv;
    std::vector<uint8_t> ciphertext;
};

// Encrypt plaintext using AES-GCM
EncryptedData encrypt(const std::vector<uint8_t>& plaintext, 
                     const std::vector<uint8_t>& key);

// Decrypt ciphertext using AES-GCM
std::vector<uint8_t> decrypt(const std::vector<uint8_t>& iv, 
                            const std::vector<uint8_t>& ciphertext, 
                            const std::vector<uint8_t>& key);

} // namespace aes
