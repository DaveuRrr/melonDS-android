#include "MelonDSAndroidIRHandler.h"
#include <android/log.h>

#define LOG_TAG "IRHandler"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

MelonDSAndroidIRHandler::MelonDSAndroidIRHandler(JniEnvHandler* jniEnvHandler, jobject irManager)
    : jniEnvHandler(jniEnvHandler), irManager(irManager)
{
    if (!jniEnvHandler || !irManager) {
        LOGE("IRHandler created with null jniEnvHandler or irManager");
        return;
    }

    LOGD("IRHandler created successfully");
}

bool MelonDSAndroidIRHandler::openSerial()
{
    if (!jniEnvHandler || !irManager) {
        LOGE("openSerial: Handler not initialized");
        return false;
    }

    JNIEnv* env = jniEnvHandler->getCurrentThreadEnv();
    if (!env) {
        LOGE("openSerial: Failed to get JNI environment");
        return false;
    }

    jclass irManagerClass = env->GetObjectClass(irManager);
    if (!irManagerClass) {
        LOGE("openSerial: Failed to get IRManager class");
        return false;
    }

    jmethodID openMethod = env->GetMethodID(irManagerClass, "openSerial", "()Z");
    if (!openMethod) {
        LOGE("openSerial: Failed to get openSerial method");
        env->DeleteLocalRef(irManagerClass);
        return false;
    }

    jboolean result = env->CallBooleanMethod(irManager, openMethod);
    env->DeleteLocalRef(irManagerClass);

    LOGD("openSerial() = %d", result);
    return result;
}

void MelonDSAndroidIRHandler::closeSerial()
{
    if (!jniEnvHandler || !irManager) {
        return;
    }

    JNIEnv* env = jniEnvHandler->getCurrentThreadEnv();
    if (!env) return;

    jclass irManagerClass = env->GetObjectClass(irManager);
    if (!irManagerClass) return;

    jmethodID closeMethod = env->GetMethodID(irManagerClass, "closeSerial", "()V");
    if (!closeMethod) {
        env->DeleteLocalRef(irManagerClass);
        return;
    }

    env->CallVoidMethod(irManager, closeMethod);
    env->DeleteLocalRef(irManagerClass);

    LOGD("closeSerial() called");
}

int MelonDSAndroidIRHandler::writeSerial(const char* data, int length)
{
    if (!jniEnvHandler || !irManager) {
        return -1;
    }

    JNIEnv* env = jniEnvHandler->getCurrentThreadEnv();
    if (!env) return -1;

    jbyteArray javaData = env->NewByteArray(length);
    if (javaData == nullptr) {
        LOGE("Failed to allocate byte array");
        return -1;
    }

    env->SetByteArrayRegion(javaData, 0, length, (const jbyte*) data);

    jclass irManagerClass = env->GetObjectClass(irManager);
    if (!irManagerClass) {
        env->DeleteLocalRef(javaData);
        return -1;
    }

    jmethodID writeMethod = env->GetMethodID(irManagerClass, "writeSerial", "([BI)I");
    if (!writeMethod) {
        env->DeleteLocalRef(irManagerClass);
        env->DeleteLocalRef(javaData);
        return -1;
    }

    jint result = env->CallIntMethod(irManager, writeMethod, javaData, length);

    env->DeleteLocalRef(irManagerClass);
    env->DeleteLocalRef(javaData);

    return result;
}

int MelonDSAndroidIRHandler::readSerial(char* buffer, int maxLength)
{
    if (!jniEnvHandler || !irManager) {
        return 0;
    }

    JNIEnv* env = jniEnvHandler->getCurrentThreadEnv();
    if (!env) return 0;

    jbyteArray javaBuffer = env->NewByteArray(maxLength);
    if (javaBuffer == nullptr) {
        LOGE("Failed to allocate byte array");
        return 0;
    }

    jclass irManagerClass = env->GetObjectClass(irManager);
    if (!irManagerClass) {
        env->DeleteLocalRef(javaBuffer);
        return 0;
    }

    jmethodID readMethod = env->GetMethodID(irManagerClass, "readSerial", "([BI)I");
    if (!readMethod) {
        env->DeleteLocalRef(irManagerClass);
        env->DeleteLocalRef(javaBuffer);
        return 0;
    }

    jint bytesRead = env->CallIntMethod(irManager, readMethod, javaBuffer, maxLength);

    if (bytesRead > 0) {
        env->GetByteArrayRegion(javaBuffer, 0, bytesRead, (jbyte*) buffer);
    }

    env->DeleteLocalRef(irManagerClass);
    env->DeleteLocalRef(javaBuffer);

    return bytesRead;
}

bool MelonDSAndroidIRHandler::isSerialOpen()
{
    if (!jniEnvHandler || !irManager) {
        return false;
    }

    JNIEnv* env = jniEnvHandler->getCurrentThreadEnv();
    if (!env) return false;

    jclass irManagerClass = env->GetObjectClass(irManager);
    if (!irManagerClass) return false;

    jmethodID isOpenMethod = env->GetMethodID(irManagerClass, "isSerialOpen", "()Z");
    if (!isOpenMethod) {
        env->DeleteLocalRef(irManagerClass);
        return false;
    }

    jboolean result = env->CallBooleanMethod(irManager, isOpenMethod);
    env->DeleteLocalRef(irManagerClass);

    return result;
}

bool MelonDSAndroidIRHandler::openTCP()
{
    if (!jniEnvHandler || !irManager) {
        LOGE("openTCP: Handler not initialized");
        return false;
    }

    JNIEnv* env = jniEnvHandler->getCurrentThreadEnv();
    if (!env) {
        LOGE("openTCP: Failed to get JNI environment");
        return false;
    }

    jclass irManagerClass = env->GetObjectClass(irManager);
    if (!irManagerClass) {
        LOGE("openTCP: Failed to get IRManager class");
        return false;
    }

    jmethodID openMethod = env->GetMethodID(irManagerClass, "openTCP", "()Z");
    if (!openMethod) {
        LOGE("openTCP: Failed to get openTCP method");
        env->DeleteLocalRef(irManagerClass);
        return false;
    }

    jboolean result = env->CallBooleanMethod(irManager, openMethod);
    env->DeleteLocalRef(irManagerClass);

    LOGD("openTCP() = %d", result);
    return result;
}

void MelonDSAndroidIRHandler::closeTCP()
{
    if (!jniEnvHandler || !irManager) {
        return;
    }

    JNIEnv* env = jniEnvHandler->getCurrentThreadEnv();
    if (!env) return;

    jclass irManagerClass = env->GetObjectClass(irManager);
    if (!irManagerClass) return;

    jmethodID closeMethod = env->GetMethodID(irManagerClass, "closeTCP", "()V");
    if (!closeMethod) {
        env->DeleteLocalRef(irManagerClass);
        return;
    }

    env->CallVoidMethod(irManager, closeMethod);
    env->DeleteLocalRef(irManagerClass);

    LOGD("closeTCP() called");
    return;
}

int MelonDSAndroidIRHandler::writeTCP(const char* data, int length)
{
    if (!jniEnvHandler || !irManager) {
        return -1;
    }

    JNIEnv* env = jniEnvHandler->getCurrentThreadEnv();
    if (!env) return -1;

    jbyteArray javaData = env->NewByteArray(length);
    if (javaData == nullptr) {
        LOGE("Failed to allocate byte array");
        return -1;
    }

    env->SetByteArrayRegion(javaData, 0, length, (const jbyte*) data);

    jclass irManagerClass = env->GetObjectClass(irManager);
    if (!irManagerClass) {
        env->DeleteLocalRef(javaData);
        return -1;
    }

    jmethodID writeMethod = env->GetMethodID(irManagerClass, "writeTCP", "([BI)I");
    if (!writeMethod) {
        env->DeleteLocalRef(irManagerClass);
        env->DeleteLocalRef(javaData);
        return -1;
    }

    jint result = env->CallIntMethod(irManager, writeMethod, javaData, length);

    env->DeleteLocalRef(irManagerClass);
    env->DeleteLocalRef(javaData);

    return result;
}

int MelonDSAndroidIRHandler::readTCP(char* buffer, int maxLength)
{
    if (!jniEnvHandler || !irManager) {
        return 0;
    }

    JNIEnv* env = jniEnvHandler->getCurrentThreadEnv();
    if (!env) return 0;

    jbyteArray javaBuffer = env->NewByteArray(maxLength);
    if (javaBuffer == nullptr) {
        LOGE("Failed to allocate byte array");
        return 0;
    }

    jclass irManagerClass = env->GetObjectClass(irManager);
    if (!irManagerClass) {
        env->DeleteLocalRef(javaBuffer);
        return 0;
    }

    jmethodID readMethod = env->GetMethodID(irManagerClass, "readTCP", "([BI)I");
    if (!readMethod) {
        env->DeleteLocalRef(irManagerClass);
        env->DeleteLocalRef(javaBuffer);
        return 0;
    }

    jint bytesRead = env->CallIntMethod(irManager, readMethod, javaBuffer, maxLength);

    if (bytesRead > 0) {
        env->GetByteArrayRegion(javaBuffer, 0, bytesRead, (jbyte*) buffer);
    }

    env->DeleteLocalRef(irManagerClass);
    env->DeleteLocalRef(javaBuffer);

    return bytesRead;
}

bool MelonDSAndroidIRHandler::isTCPOpen()
{
    if (!jniEnvHandler || !irManager) {
        return false;
    }

    JNIEnv* env = jniEnvHandler->getCurrentThreadEnv();
    if (!env) return false;

    jclass irManagerClass = env->GetObjectClass(irManager);
    if (!irManagerClass) return false;

    jmethodID isOpenMethod = env->GetMethodID(irManagerClass, "isTCPOpen", "()Z");
    if (!isOpenMethod) {
        env->DeleteLocalRef(irManagerClass);
        return false;
    }

    jboolean result = env->CallBooleanMethod(irManager, isOpenMethod);
    env->DeleteLocalRef(irManagerClass);

    return result;
}

bool MelonDSAndroidIRHandler::hasDataAvailable()
{
    if (!jniEnvHandler || !irManager) {
        return false;
    }

    JNIEnv* env = jniEnvHandler->getCurrentThreadEnv();
    if (!env) return false;

    jclass irManagerClass = env->GetObjectClass(irManager);
    if (!irManagerClass) return false;

    jmethodID hasDataMethod = env->GetMethodID(irManagerClass, "hasDataAvailable", "()Z");
    if (!hasDataMethod) {
        env->DeleteLocalRef(irManagerClass);
        return false;
    }

    jboolean result = env->CallBooleanMethod(irManager, hasDataMethod);
    env->DeleteLocalRef(irManagerClass);

    return result;
}

MelonDSAndroidIRHandler::~MelonDSAndroidIRHandler()
{
}
