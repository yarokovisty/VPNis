# Application.mk for :data:vpn native layer (hev_tun2socks + hev-socks5-tunnel).
#
# ABI targets: arm64-v8a (devices) + x86_64 (emulator).
# minSdk is 26 (matches project-wide defaultConfig.minSdk in AndroidCommon.kt).
# APP_SUPPORT_FLEXIBLE_PAGE_SIZES enables 16 KiB page-size support required by
# hev-socks5-tunnel's ldflags (-Wl,-z,max-page-size=16384).
# APP_STL is omitted — hev and our shim are pure C and do not use the C++ STL.

APP_ABI                        := arm64-v8a x86_64
APP_PLATFORM                   := android-26
APP_SUPPORT_FLEXIBLE_PAGE_SIZES := true
NDK_TOOLCHAIN_VERSION          := clang
APP_OPTIM                      := release
