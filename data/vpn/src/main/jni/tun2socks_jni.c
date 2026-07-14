/*
 * tun2socks_jni.c — JNI shim bridging Kotlin Tun2SocksBridge to hev-socks5-tunnel.
 *
 * Exposed symbols (package org.yarokovisty.vpnis.data.vpn, object Tun2SocksBridge):
 *   nativeStart(configYaml: String, tunFd: Int): Int
 *   nativeStop()
 *
 * nativeStart BLOCKS until the tunnel quits or an error occurs; callers must run
 * it on a background thread / coroutine dispatcher.
 */

#include <jni.h>
#include <string.h>

#include "hev-socks5-tunnel/include/hev-socks5-tunnel.h"

JNIEXPORT jint JNICALL
Java_org_yarokovisty_vpnis_data_vpn_Tun2SocksBridge_nativeStart(
        JNIEnv *env, jobject thiz, jstring configYaml, jint tunFd)
{
    const char *cfg = (*env)->GetStringUTFChars(env, configYaml, NULL);
    if (!cfg) {
        return -1;
    }
    int result = hev_socks5_tunnel_main_from_str(
            (const unsigned char *)cfg,
            (unsigned int)strlen(cfg),
            (int)tunFd);
    (*env)->ReleaseStringUTFChars(env, configYaml, cfg);
    return (jint)result;
}

JNIEXPORT void JNICALL
Java_org_yarokovisty_vpnis_data_vpn_Tun2SocksBridge_nativeStop(
        JNIEnv *env, jobject thiz)
{
    hev_socks5_tunnel_quit();
}
