#pragma once

#include <vector>
#include <cstdint>

namespace shamir {

// Split a secret into shares
std::vector<std::vector<uint8_t>> splitSecret(const std::vector<uint8_t>& secret, 
                                            int totalShares, 
                                            int threshold);

// Recover a secret from shares
std::vector<uint8_t> recoverSecret(const std::vector<int>& shareIds, 
                                  const std::vector<std::vector<uint8_t>>& shares);

} // namespace shamir
