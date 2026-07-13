#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>

extern JavaVM *g_jvm;

static jclass g_dnsResolverClass = NULL;
static jmethodID g_onResolutionCompleteMethod = NULL;
static pthread_mutex_t dns_jni_init_mutex = PTHREAD_MUTEX_INITIALIZER;

extern void StartResolveBootstrap(
        int64_t id,
        const char* host,
        const char* protocol,
        const char* resolvedUpstream,
        const char* originalUpstream,
        int bypass);

// Helper to lazily cache Kotlin callback
void initDnsJni(JNIEnv* env) {
    if (g_dnsResolverClass != NULL) {
        return;
    }

    pthread_mutex_lock(&dns_jni_init_mutex);
    if (g_dnsResolverClass == NULL) {
        jclass clazz = (*env)->FindClass(env, "com/zaneschepke/tunnel/backend/dns/NativeDnsResolver");
        if (clazz == NULL) {
            pthread_mutex_unlock(&dns_jni_init_mutex);
            return;
        }
        g_dnsResolverClass = (*env)->NewGlobalRef(env, clazz);
        (*env)->DeleteLocalRef(env, clazz);

        g_onResolutionCompleteMethod = (*env)->GetStaticMethodID(
                env,
                g_dnsResolverClass,
                "onResolutionComplete",
                "(JLjava/lang/String;)V"
        );
    }
    pthread_mutex_unlock(&dns_jni_init_mutex);
}


// Called by Go to push the result back to Kotlin
void NotifyDnsResult(int64_t id, const char* result) {
    if (g_jvm == NULL || g_dnsResolverClass == NULL || g_onResolutionCompleteMethod == NULL) return;

    JNIEnv *env = NULL;
    jint rs = (*g_jvm)->GetEnv(g_jvm, (void **)&env, JNI_VERSION_1_6);

    if (rs == JNI_EDETACHED) {
        if ((*g_jvm)->AttachCurrentThreadAsDaemon(g_jvm, (JNIEnv **)&env, NULL) != JNI_OK) {
            return;
        }
    } else if (rs != JNI_OK) {
        return;
    }

    jstring jresult = (*env)->NewStringUTF(env, result);

    // Call our NativeDnsResolver.onResolutionComplete
    (*env)->CallStaticVoidMethod(env, g_dnsResolverClass, g_onResolutionCompleteMethod, (jlong)id, jresult);

    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
    }

    (*env)->DeleteLocalRef(env, jresult);
}

JNIEXPORT void JNICALL
Java_com_zaneschepke_tunnel_backend_dns_NativeDnsResolver_startBootstrapResolution(
        JNIEnv* env,
        jclass clazz,
        jlong id,
        jstring host,
        jstring protocol,
        jstring resolvedUpstream,
        jstring originalUpstream,
        jint bypass)
{
    initDnsJni(env);

    const char* chost = (*env)->GetStringUTFChars(env, host, NULL);
    const char* cprotocol = (*env)->GetStringUTFChars(env, protocol, NULL);
    const char* cresolvedUpstream = (*env)->GetStringUTFChars(env, resolvedUpstream, NULL);
    const char* coriginalUpstream = (*env)->GetStringUTFChars(env, originalUpstream, NULL);

    // Defensive copies to prevent Go from seeing MTE tagged JVM memory
    char* safe_host = strdup(chost);
    char* safe_protocol = strdup(cprotocol);
    char* safe_res_up = strdup(cresolvedUpstream);
    char* safe_orig_up = strdup(coriginalUpstream);

    (*env)->ReleaseStringUTFChars(env, host, chost);
    (*env)->ReleaseStringUTFChars(env, protocol, cprotocol);
    (*env)->ReleaseStringUTFChars(env, resolvedUpstream, cresolvedUpstream);
    (*env)->ReleaseStringUTFChars(env, originalUpstream, coriginalUpstream);

    StartResolveBootstrap(
            (int64_t)id,
            safe_host,
            safe_protocol,
            safe_res_up,
            safe_orig_up,
            bypass ? 1 : 0
    );

    free(safe_host);
    free(safe_protocol);
    free(safe_res_up);
    free(safe_orig_up);
}