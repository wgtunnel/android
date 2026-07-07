#include <jni.h>
#include <stdlib.h>

struct go_string { const char *str; long n; };

extern char* ResolveBootstrap(
        const char* host,
        const char* protocol,
        const char* resolvedUpstream,
        const char* originalUpstream,
        int bypass);

JNIEXPORT jstring JNICALL
Java_com_zaneschepke_tunnel_backend_dns_NativeDnsResolver_resolveBootstrap(
        JNIEnv* env,
        jclass clazz,
        jstring host,
        jstring protocol,
        jstring resolvedUpstream,
        jstring originalUpstream,
        jint bypass)
{
    if (host == NULL || protocol == NULL || resolvedUpstream == NULL || originalUpstream == NULL) {
        return (*env)->NewStringUTF(env, "ERR|invalid arguments");
    }

    const char* chost             = (*env)->GetStringUTFChars(env, host, NULL);
    const char* cprotocol         = (*env)->GetStringUTFChars(env, protocol, NULL);
    const char* cresolvedUpstream = (*env)->GetStringUTFChars(env, resolvedUpstream, NULL);
    const char* coriginalUpstream = (*env)->GetStringUTFChars(env, originalUpstream, NULL);

    if (chost == NULL || cprotocol == NULL || cresolvedUpstream == NULL || coriginalUpstream == NULL) {
        return (*env)->NewStringUTF(env, "ERR|out of memory");
    }

    char* resultC = ResolveBootstrap(
            chost,
            cprotocol,
            cresolvedUpstream,
            coriginalUpstream,
            bypass ? 1 : 0
    );

    (*env)->ReleaseStringUTFChars(env, host, chost);
    (*env)->ReleaseStringUTFChars(env, protocol, cprotocol);
    (*env)->ReleaseStringUTFChars(env, resolvedUpstream, cresolvedUpstream);
    (*env)->ReleaseStringUTFChars(env, originalUpstream, coriginalUpstream);

    if (resultC == NULL) {
        return (*env)->NewStringUTF(env, "ERR|null response");
    }

    jstring jresult = (*env)->NewStringUTF(env, resultC);
    free(resultC);
    return jresult;
}