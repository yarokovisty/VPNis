# Android.mk for :data:vpn native layer.
#
# Produces two .so files packaged into the AAR:
#   libhev-socks5-tunnel.so  — hev upstream shared library (built by the submodule's Android.mk)
#   libhev_tun2socks.so      — our JNI shim; links hev via DT_NEEDED (minSdk 26 allows this)
#
# Include order matters: hev's Android.mk uses "TOP_PATH := $(call my-dir)" as its very first
# statement, so it correctly captures its own directory regardless of who included it.

MY_DIR := $(call my-dir)

# Pull in hev-socks5-tunnel (and its transitive static deps: yaml, lwip, hev-task-system).
include $(MY_DIR)/hev-socks5-tunnel/Android.mk

# ---- JNI shim module --------------------------------------------------------
LOCAL_PATH := $(MY_DIR)

include $(CLEAR_VARS)

LOCAL_MODULE        := hev_tun2socks
LOCAL_SRC_FILES     := tun2socks_jni.c
LOCAL_C_INCLUDES    := $(MY_DIR)/hev-socks5-tunnel/include
LOCAL_SHARED_LIBRARIES := hev-socks5-tunnel
LOCAL_LDLIBS        += -llog

include $(BUILD_SHARED_LIBRARY)
