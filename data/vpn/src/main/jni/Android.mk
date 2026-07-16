# Android.mk for :data:vpn native layer.
#
# Produces two .so files packaged into the AAR:
#   libhev-socks5-tunnel.so  — hev upstream library, built here WITHOUT hev-jni.c
#   libhev_tun2socks.so      — our JNI shim; links hev via DT_NEEDED (minSdk 26 allows this)
#
# WHY we replicate hev's build instead of including hev-socks5-tunnel/Android.mk:
#   The upstream Android.mk compiles ALL src/*.c files via rwildcard, which pulls in
#   hev-jni.c.  That file defines JNI_OnLoad and tries to find "hev/htproxy/TProxyService"
#   (a class that does not exist in VPNis).  On Android 16 (API 36) strict JNI checking
#   turns RegisterNatives(env, null, …) into a SIGABRT that crashes the whole process
#   the moment System.loadLibrary("hev_tun2socks") is called.
#
#   hev-socks5-tunnel/ is a git submodule (heiher/hev-socks5-tunnel, SHA pinned by ADR-0001).
#   We MUST NOT edit any file inside the submodule directory, so the fix lives here.
#   We still delegate the third-party dependency builds (yaml/lwip/hev-task-system) to
#   the submodule's own Android.mk files — those are unaffected by the fix.

MY_DIR := $(call my-dir)
HEV_DIR := $(MY_DIR)/hev-socks5-tunnel

# ---- Pull in third-party static deps from submodule (yaml, lwip, hev-task-system) --------
# The upstream hev Android.mk guards each include with $(filter $(modules-get-list),<name>)
# to avoid double-inclusion; reuse the same guards here so this file can be included
# multiple times safely in exotic setups.
ifeq ($(filter $(modules-get-list),yaml),)
    include $(HEV_DIR)/third-part/yaml/Android.mk
endif
ifeq ($(filter $(modules-get-list),lwip),)
    include $(HEV_DIR)/third-part/lwip/Android.mk
endif
ifeq ($(filter $(modules-get-list),hev-task-system),)
    include $(HEV_DIR)/third-part/hev-task-system/Android.mk
endif

# ---- Build libhev-socks5-tunnel WITHOUT hev-jni.c ------------------------------------
#
# Replicates the logic in:
#   hev-socks5-tunnel/build.mk      (rwildcard + SRCFILES)
#   hev-socks5-tunnel/Android.mk    (module definition)
# with a single change: hev-jni.c is excluded from LOCAL_SRC_FILES.

LOCAL_PATH := $(HEV_DIR)
HEV_SRCDIR := $(LOCAL_PATH)/src

# Recursive wildcard helper (identical to the one in hev-socks5-tunnel/build.mk).
rwildcard = $(foreach d,$(wildcard $1*),$(call rwildcard,$d/,$2) $(filter $(subst *,%,$2),$d))

# Collect every .c and .S under src/, then strip hev-jni.c.
HEV_ALL_SRCS  := $(call rwildcard,$(HEV_SRCDIR)/,*.c *.S)
HEV_SRCS      := $(filter-out %/hev-jni.c,$(HEV_ALL_SRCS))

# Paths must be relative to LOCAL_PATH for ndk-build.
HEV_LOCAL_SRCS := $(patsubst $(HEV_SRCDIR)/%,src/%,$(HEV_SRCS))

include $(CLEAR_VARS)

LOCAL_MODULE        := hev-socks5-tunnel
LOCAL_SRC_FILES     := $(HEV_LOCAL_SRCS)
LOCAL_C_INCLUDES    := \
    $(LOCAL_PATH)/src \
    $(LOCAL_PATH)/src/misc \
    $(LOCAL_PATH)/src/core/include \
    $(LOCAL_PATH)/third-part/yaml/include \
    $(LOCAL_PATH)/third-part/lwip/src/include \
    $(LOCAL_PATH)/third-part/lwip/src/ports/include \
    $(LOCAL_PATH)/third-part/hev-task-system/include
# ENABLE_LIBRARY exposes hev_socks5_tunnel_main_from_str / hev_socks5_tunnel_quit.
# FD_SET_DEFINED / SOCKLEN_T_DEFINED suppress conflicting typedefs from bionic headers.
# COMMIT_ID is cosmetic (logged at tunnel start); use a fixed string so the build
# does not depend on git being available in the CI sandbox.
LOCAL_CFLAGS        += \
    -DFD_SET_DEFINED \
    -DSOCKLEN_T_DEFINED \
    -DENABLE_LIBRARY \
    -DCOMMIT_ID=\"vpnis\"
LOCAL_STATIC_LIBRARIES := yaml lwip hev-task-system
# 16 KiB page-size alignment required for modern Android devices.
LOCAL_LDFLAGS       += -Wl,-z,max-page-size=16384
LOCAL_LDFLAGS       += -Wl,-z,common-page-size=16384

include $(BUILD_SHARED_LIBRARY)

# ---- JNI shim module --------------------------------------------------------
LOCAL_PATH := $(MY_DIR)

include $(CLEAR_VARS)

LOCAL_MODULE            := hev_tun2socks
LOCAL_SRC_FILES         := tun2socks_jni.c
LOCAL_C_INCLUDES        := $(HEV_DIR)/include
LOCAL_SHARED_LIBRARIES  := hev-socks5-tunnel
LOCAL_LDLIBS            += -llog

include $(BUILD_SHARED_LIBRARY)
