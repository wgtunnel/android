#include <jni.h>
#include <stdlib.h>

struct go_string { const char *str; long n; };

extern char* ResolveBootstrap(
        const char* host,
        const char* protocol,
        const char* upstream,
        const char* underlyingDnsServers,
        int bypass);

JNIEXPORT jstring JNICALL
Java_com_zaneschepke_tunnel_DnsConfigManager_resolveBootstrap(
        JNIEnv* env,
        jclass clazz,
        jstring host,
        jstring protocol,
        jstring upstream,
        jstring underlyingDnsServers,
        jint bypass)
{
    if (host == NULL || protocol == NULL || upstream == NULL || underlyingDnsServers == NULL) {
        return (*env)->NewStringUTF(env, "ERR|invalid arguments");
    }

    const char* chost       = (*env)->GetStringUTFChars(env, host, NULL);
    const char* cprotocol   = (*env)->GetStringUTFChars(env, protocol, NULL);
    const char* cupstream   = (*env)->GetStringUTFChars(env, upstream, NULL);
    const char* cunderlying = (*env)->GetStringUTFChars(env, underlyingDnsServers, NULL);

    if (chost == NULL || cprotocol == NULL || cupstream == NULL || cunderlying == NULL) {
        return (*env)->NewStringUTF(env, "ERR|out of memory");
    }

    char* resultC = ResolveBootstrap(
            chost,
            cprotocol,
            cupstream,
            cunderlying,
            bypass ? 1 : 0
    );

    (*env)->ReleaseStringUTFChars(env, host, chost);
    (*env)->ReleaseStringUTFChars(env, protocol, cprotocol);
    (*env)->ReleaseStringUTFChars(env, upstream, cupstream);
    (*env)->ReleaseStringUTFChars(env, underlyingDnsServers, cunderlying);

    if (resultC == NULL) {
        return (*env)->NewStringUTF(env, "ERR|null response");
    }

    jstring jresult = (*env)->NewStringUTF(env, resultC);
    free(resultC);
    return jresult;
}