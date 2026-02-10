#include <jni.h>
#include <string>
#include <cstdlib>
#include "node.h"
#include <pthread.h>
#include <unistd.h>
#include <android/log.h>

#define TAG "WIVERN"

int pipe_stdout[2];
int pipe_stderr[2];

void *thread_stderr_func(void*) {
    ssize_t sz;
    char buf[2048];
    while ((sz = read(pipe_stderr[0], buf, sizeof(buf) - 1)) > 0) {
        if (buf[sz - 1] == '\n') --sz;
        buf[sz] = 0;
        __android_log_write(ANDROID_LOG_ERROR, TAG, buf);
    }
    return 0;
}

void *thread_stdout_func(void*) {
    ssize_t sz;
    char buf[2048];
    while ((sz = read(pipe_stdout[0], buf, sizeof(buf) - 1)) > 0) {
        if (buf[sz - 1] == '\n') --sz;
        buf[sz] = 0;
        __android_log_write(ANDROID_LOG_INFO, TAG, buf);
    }
    return 0;
}

int start_redirecting() {
    setvbuf(stdout, 0, _IONBF, 0);
    pipe(pipe_stdout);
    dup2(pipe_stdout[1], STDOUT_FILENO);

    setvbuf(stderr, 0, _IONBF, 0);
    pipe(pipe_stderr);
    dup2(pipe_stderr[1], STDERR_FILENO);

    pthread_t t1, t2;
    if (pthread_create(&t1, 0, thread_stdout_func, 0) == -1) return -1;
    pthread_detach(t1);
    if (pthread_create(&t2, 0, thread_stderr_func, 0) == -1) return -1;
    pthread_detach(t2);
    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_sillytavern_app_NodeEngine_setICUData(
    JNIEnv *env, jobject, jstring icuDir) {
    const char* dir = env->GetStringUTFChars(icuDir, 0);
    setenv("NODE_ICU_DATA", dir, 1);
    __android_log_print(ANDROID_LOG_INFO, TAG, "Set NODE_ICU_DATA=%s", dir);
    env->ReleaseStringUTFChars(icuDir, dir);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_sillytavern_app_NodeEngine_startNodeWithArguments(
    JNIEnv *env, jobject, jobjectArray arguments) {

    jsize argc = env->GetArrayLength(arguments);

    int buf_size = 0;
    for (int i = 0; i < argc; i++) {
        jstring arg = (jstring)env->GetObjectArrayElement(arguments, i);
        buf_size += env->GetStringUTFLength(arg) + 1;
    }

    char* buf = (char*)calloc(buf_size, sizeof(char));
    char* argv[argc];
    char* pos = buf;

    for (int i = 0; i < argc; i++) {
        jstring arg = (jstring)env->GetObjectArrayElement(arguments, i);
        const char* str = env->GetStringUTFChars(arg, 0);
        strcpy(pos, str);
        argv[i] = pos;
        pos += strlen(str) + 1;
        env->ReleaseStringUTFChars(arg, str);
    }

    if (start_redirecting() == -1) {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Failed to redirect stdout/stderr");
    }

    // Log all args
    for (int i = 0; i < argc; i++) {
        __android_log_print(ANDROID_LOG_INFO, TAG, "argv[%d] = %s", i, argv[i]);
    }

    __android_log_write(ANDROID_LOG_INFO, TAG, "Starting Node.js...");
    int result = node::Start(argc, argv);
    __android_log_print(ANDROID_LOG_INFO, TAG, "Node.js exited with code: %d", result);
    return jint(result);
}
