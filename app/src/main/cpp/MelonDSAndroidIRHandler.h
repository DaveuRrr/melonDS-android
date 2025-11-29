#ifndef MELONDSANDROIDIRHANDLER_H
#define MELONDSANDROIDIRHANDLER_H

#include <jni.h>
#include <AndroidIRHandler.h>
#include "JniEnvHandler.h"

class MelonDSAndroidIRHandler : public MelonDSAndroid::AndroidIRHandler {
private:
    JniEnvHandler* jniEnvHandler;
    jobject irManager;

public:
    MelonDSAndroidIRHandler(JniEnvHandler* jniEnvHandler, jobject irManager);
    bool openSerial() override;
    void closeSerial() override;
    int writeSerial(const char* data, int length) override;
    int readSerial(char* buffer, int maxLength) override;
    bool isSerialOpen() override;
    bool openTCP() override;
    void closeTCP() override;
    int writeTCP(const char* data, int length) override;
    int readTCP(char* buffer, int maxLength) override;
    bool isTCPOpen() override;

    bool hasDataAvailable() override;
    virtual ~MelonDSAndroidIRHandler();
};

#endif //MELONDSANDROIDIRHANDLER_H
