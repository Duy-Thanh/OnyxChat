#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>

#include "kyber/kyber.h"
#include "dilithium/dilithium.h"
#include "aes/aes_gcm.h"
#include "shamir/shamir.h"

// Define log tag
#define LOG_TAG "PQC_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Helper functions for JNI
namespace {

// Convert Java byte array to C++ vector
std::vector<uint8_t> jbyteArrayToVector(JNIEnv* env, jbyteArray array) {
    if (array == nullptr) {
        return std::vector<uint8_t>();
    }
    
    jsize length = env->GetArrayLength(array);
    std::vector<uint8_t> result(length);
    
    jbyte* elements = env->GetByteArrayElements(array, nullptr);
    if (elements != nullptr) {
        memcpy(result.data(), elements, length);
        env->ReleaseByteArrayElements(array, elements, JNI_ABORT);
    }
    
    return result;
}

// Convert C++ vector to Java byte array
jbyteArray vectorToJbyteArray(JNIEnv* env, const std::vector<uint8_t>& data) {
    jbyteArray result = env->NewByteArray(data.size());
    if (result == nullptr) {
        return nullptr;
    }
    
    env->SetByteArrayRegion(result, 0, data.size(), reinterpret_cast<const jbyte*>(data.data()));
    return result;
}

// Convert Java int array to C++ vector
std::vector<int> jintArrayToVector(JNIEnv* env, jintArray array) {
    if (array == nullptr) {
        return std::vector<int>();
    }
    
    jsize length = env->GetArrayLength(array);
    std::vector<int> result(length);
    
    jint* elements = env->GetIntArrayElements(array, nullptr);
    if (elements != nullptr) {
        for (jsize i = 0; i < length; i++) {
            result[i] = elements[i];
        }
        env->ReleaseIntArrayElements(array, elements, JNI_ABORT);
    }
    
    return result;
}

// Convert Java 2D byte array to C++ vector of vectors
std::vector<std::vector<uint8_t>> jbyteArray2DToVector(JNIEnv* env, jobjectArray array) {
    if (array == nullptr) {
        return std::vector<std::vector<uint8_t>>();
    }
    
    jsize length = env->GetArrayLength(array);
    std::vector<std::vector<uint8_t>> result(length);
    
    for (jsize i = 0; i < length; i++) {
        jbyteArray row = (jbyteArray)env->GetObjectArrayElement(array, i);
        if (row != nullptr) {
            result[i] = jbyteArrayToVector(env, row);
            env->DeleteLocalRef(row);
        }
    }
    
    return result;
}

// Convert C++ vector of vectors to Java 2D byte array
jobjectArray vectorToJbyteArray2D(JNIEnv* env, const std::vector<std::vector<uint8_t>>& data) {
    jclass byteArrayClass = env->FindClass("[B");
    if (byteArrayClass == nullptr) {
        return nullptr;
    }
    
    jobjectArray result = env->NewObjectArray(data.size(), byteArrayClass, nullptr);
    if (result == nullptr) {
        return nullptr;
    }
    
    for (size_t i = 0; i < data.size(); i++) {
        jbyteArray row = vectorToJbyteArray(env, data[i]);
        if (row != nullptr) {
            env->SetObjectArrayElement(result, i, row);
            env->DeleteLocalRef(row);
        }
    }
    
    return result;
}

} // anonymous namespace

// JNI function implementations
extern "C" {

// Initialize the PQC module
JNIEXPORT void JNICALL
Java_com_nekkochan_onyxchat_crypto_PQCProvider_nativeInitialize(JNIEnv* env, jclass clazz) {
    LOGI("Initializing PQC module");
    kyber::initialize();
    dilithium::initialize();
}

// Generate a Kyber key pair
JNIEXPORT jobjectArray JNICALL
Java_com_nekkochan_onyxchat_crypto_PQCProvider_nativeGenerateKyberKeyPair(JNIEnv* env, jclass clazz, jint variant) {
    LOGI("Generating Kyber key pair with variant %d", variant);
    
    kyber::Variant kyberVariant;
    switch (variant) {
        case 1:
            kyberVariant = kyber::Variant::KYBER_512;
            break;
        case 2:
            kyberVariant = kyber::Variant::KYBER_768;
            break;
        case 3:
            kyberVariant = kyber::Variant::KYBER_1024;
            break;
        default:
            kyberVariant = kyber::Variant::KYBER_768;  // Default to KYBER_768
    }
    
    kyber::KeyPair keyPair = kyber::generateKeyPair(kyberVariant);
    
    // Create a 2D byte array to return [publicKey, privateKey]
    jclass byteArrayClass = env->FindClass("[B");
    if (byteArrayClass == nullptr) {
        return nullptr;
    }
    
    jobjectArray result = env->NewObjectArray(2, byteArrayClass, nullptr);
    if (result == nullptr) {
        return nullptr;
    }
    
    jbyteArray publicKey = vectorToJbyteArray(env, keyPair.publicKey);
    jbyteArray privateKey = vectorToJbyteArray(env, keyPair.privateKey);
    
    env->SetObjectArrayElement(result, 0, publicKey);
    env->SetObjectArrayElement(result, 1, privateKey);
    
    env->DeleteLocalRef(publicKey);
    env->DeleteLocalRef(privateKey);
    
    return result;
}

// Encapsulate a shared secret using a Kyber public key
JNIEXPORT jobjectArray JNICALL
Java_com_nekkochan_onyxchat_crypto_PQCProvider_nativeEncapsulateKey(JNIEnv* env, jclass clazz, jbyteArray publicKey) {
    LOGI("Encapsulating shared secret");
    
    std::vector<uint8_t> publicKeyVec = jbyteArrayToVector(env, publicKey);
    kyber::EncapsulationResult result = kyber::encapsulate(publicKeyVec);
    
    // Create a 2D byte array to return [ciphertext, sharedSecret]
    jclass byteArrayClass = env->FindClass("[B");
    if (byteArrayClass == nullptr) {
        return nullptr;
    }
    
    jobjectArray jresult = env->NewObjectArray(2, byteArrayClass, nullptr);
    if (jresult == nullptr) {
        return nullptr;
    }
    
    jbyteArray ciphertext = vectorToJbyteArray(env, result.ciphertext);
    jbyteArray sharedSecret = vectorToJbyteArray(env, result.sharedSecret);
    
    env->SetObjectArrayElement(jresult, 0, ciphertext);
    env->SetObjectArrayElement(jresult, 1, sharedSecret);
    
    env->DeleteLocalRef(ciphertext);
    env->DeleteLocalRef(sharedSecret);
    
    return jresult;
}

// Decapsulate a shared secret using a Kyber private key and ciphertext
JNIEXPORT jbyteArray JNICALL
Java_com_nekkochan_onyxchat_crypto_PQCProvider_nativeDecapsulateKey(JNIEnv* env, jclass clazz, jbyteArray privateKey, jbyteArray ciphertext) {
    LOGI("Decapsulating shared secret");
    
    std::vector<uint8_t> privateKeyVec = jbyteArrayToVector(env, privateKey);
    std::vector<uint8_t> ciphertextVec = jbyteArrayToVector(env, ciphertext);
    
    std::vector<uint8_t> sharedSecret = kyber::decapsulate(privateKeyVec, ciphertextVec);
    
    return vectorToJbyteArray(env, sharedSecret);
}

// Generate a Dilithium key pair
JNIEXPORT jobjectArray JNICALL
Java_com_nekkochan_onyxchat_crypto_PQCProvider_nativeGenerateDilithiumKeyPair(JNIEnv* env, jclass clazz, jint variant) {
    LOGI("Generating Dilithium key pair with variant %d", variant);
    
    dilithium::Variant dilithiumVariant;
    switch (variant) {
        case 1:
            dilithiumVariant = dilithium::Variant::DILITHIUM_2;
            break;
        case 2:
            dilithiumVariant = dilithium::Variant::DILITHIUM_3;
            break;
        case 3:
            dilithiumVariant = dilithium::Variant::DILITHIUM_5;
            break;
        default:
            dilithiumVariant = dilithium::Variant::DILITHIUM_3;  // Default to DILITHIUM_3
    }
    
    dilithium::KeyPair keyPair = dilithium::generateKeyPair(dilithiumVariant);
    
    // Create a 2D byte array to return [publicKey, privateKey]
    jclass byteArrayClass = env->FindClass("[B");
    if (byteArrayClass == nullptr) {
        return nullptr;
    }
    
    jobjectArray result = env->NewObjectArray(2, byteArrayClass, nullptr);
    if (result == nullptr) {
        return nullptr;
    }
    
    jbyteArray publicKey = vectorToJbyteArray(env, keyPair.publicKey);
    jbyteArray privateKey = vectorToJbyteArray(env, keyPair.privateKey);
    
    env->SetObjectArrayElement(result, 0, publicKey);
    env->SetObjectArrayElement(result, 1, privateKey);
    
    env->DeleteLocalRef(publicKey);
    env->DeleteLocalRef(privateKey);
    
    return result;
}

// Sign a message using a Dilithium private key
JNIEXPORT jbyteArray JNICALL
Java_com_nekkochan_onyxchat_crypto_PQCProvider_nativeSignMessage(JNIEnv* env, jclass clazz, jbyteArray privateKey, jbyteArray message) {
    LOGI("Signing message");
    
    std::vector<uint8_t> privateKeyVec = jbyteArrayToVector(env, privateKey);
    std::vector<uint8_t> messageVec = jbyteArrayToVector(env, message);
    
    std::vector<uint8_t> signature = dilithium::sign(privateKeyVec, messageVec);
    
    return vectorToJbyteArray(env, signature);
}

// Verify a signature using a Dilithium public key
JNIEXPORT jboolean JNICALL
Java_com_nekkochan_onyxchat_crypto_PQCProvider_nativeVerifySignature(JNIEnv* env, jclass clazz, jbyteArray publicKey, jbyteArray message, jbyteArray signature) {
    LOGI("Verifying signature");
    
    std::vector<uint8_t> publicKeyVec = jbyteArrayToVector(env, publicKey);
    std::vector<uint8_t> messageVec = jbyteArrayToVector(env, message);
    std::vector<uint8_t> signatureVec = jbyteArrayToVector(env, signature);
    
    bool isValid = dilithium::verify(publicKeyVec, messageVec, signatureVec);
    
    return isValid ? JNI_TRUE : JNI_FALSE;
}

// Encrypt data using AES-GCM
JNIEXPORT jobjectArray JNICALL
Java_com_nekkochan_onyxchat_crypto_PQCProvider_nativeEncrypt(JNIEnv* env, jclass clazz, jbyteArray data, jbyteArray key) {
    LOGI("Encrypting data");
    
    std::vector<uint8_t> dataVec = jbyteArrayToVector(env, data);
    std::vector<uint8_t> keyVec = jbyteArrayToVector(env, key);
    
    aes::EncryptedData encryptedData = aes::encrypt(dataVec, keyVec);
    
    // Create a 2D byte array to return [iv, ciphertext]
    jclass byteArrayClass = env->FindClass("[B");
    if (byteArrayClass == nullptr) {
        return nullptr;
    }
    
    jobjectArray result = env->NewObjectArray(2, byteArrayClass, nullptr);
    if (result == nullptr) {
        return nullptr;
    }
    
    jbyteArray iv = vectorToJbyteArray(env, encryptedData.iv);
    jbyteArray ciphertext = vectorToJbyteArray(env, encryptedData.ciphertext);
    
    env->SetObjectArrayElement(result, 0, iv);
    env->SetObjectArrayElement(result, 1, ciphertext);
    
    env->DeleteLocalRef(iv);
    env->DeleteLocalRef(ciphertext);
    
    return result;
}

// Decrypt data using AES-GCM
JNIEXPORT jbyteArray JNICALL
Java_com_nekkochan_onyxchat_crypto_PQCProvider_nativeDecrypt(JNIEnv* env, jclass clazz, jbyteArray iv, jbyteArray ciphertext, jbyteArray key) {
    LOGI("Decrypting data");
    
    std::vector<uint8_t> ivVec = jbyteArrayToVector(env, iv);
    std::vector<uint8_t> ciphertextVec = jbyteArrayToVector(env, ciphertext);
    std::vector<uint8_t> keyVec = jbyteArrayToVector(env, key);
    
    std::vector<uint8_t> plaintext = aes::decrypt(ivVec, ciphertextVec, keyVec);
    
    return vectorToJbyteArray(env, plaintext);
}

// Split a secret using Shamir's Secret Sharing
JNIEXPORT jobjectArray JNICALL
Java_com_nekkochan_onyxchat_crypto_SecretSharing_nativeSplitSecret(JNIEnv* env, jclass clazz, jbyteArray secret, jint totalShares, jint threshold) {
    LOGI("Splitting secret into %d shares with threshold %d", totalShares, threshold);
    
    std::vector<uint8_t> secretVec = jbyteArrayToVector(env, secret);
    
    try {
        std::vector<std::vector<uint8_t>> shares = shamir::splitSecret(secretVec, totalShares, threshold);
        return vectorToJbyteArray2D(env, shares);
    } catch (const std::exception& e) {
        LOGE("Error splitting secret: %s", e.what());
        jclass exceptionClass = env->FindClass("java/lang/IllegalArgumentException");
        if (exceptionClass != nullptr) {
            env->ThrowNew(exceptionClass, e.what());
        }
        return nullptr;
    }
}

// Recover a secret using Shamir's Secret Sharing
JNIEXPORT jbyteArray JNICALL
Java_com_nekkochan_onyxchat_crypto_SecretSharing_nativeRecoverSecret(JNIEnv* env, jclass clazz, jintArray shareIds, jobjectArray shares) {
    LOGI("Recovering secret from shares");
    
    std::vector<int> shareIdsVec = jintArrayToVector(env, shareIds);
    std::vector<std::vector<uint8_t>> sharesVec = jbyteArray2DToVector(env, shares);
    
    try {
        std::vector<uint8_t> secret = shamir::recoverSecret(shareIdsVec, sharesVec);
        return vectorToJbyteArray(env, secret);
    } catch (const std::exception& e) {
        LOGE("Error recovering secret: %s", e.what());
        jclass exceptionClass = env->FindClass("java/lang/IllegalArgumentException");
        if (exceptionClass != nullptr) {
            env->ThrowNew(exceptionClass, e.what());
        }
        return nullptr;
    }
}

} // extern "C"
