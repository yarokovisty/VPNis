# Issue #111 — final diagnosis: Xray-core version (26.5.9 vs v2rayNG 25.3.31)

## Bug
Real VLESS+REALITY+xtls-rprx-vision tunnel comes up but passes ~no return traffic. Large reality
handshakes to the server stall (SYN reaches server → TCP ESTAB, but the first ~1.7KB data segment
— the vision-padded reality ClientHello — is never ACKed; Send-Q stuck, cwnd:1, retrans). SMALL
first-segments (228-byte DoT ClientHellos to 1.1.1.1:853) tunnel fine. Pages ERR_TIMED_OUT.

## Environment
Pixel 7 / Android 16. ISP = Rostelecom (WiFi SSID "IGD_Rostelecom"), RUSSIA — known for DPI (TSPU).
Server 144.31.104.124:443, sni=dl.google.com, flow=xtls-rprx-vision, fp=chrome, reality.

## What was FIXED and verified (all landed on branch fix/111-tun-mtu-return-path, PR #112)
1. MTU 8500→1500 (red herring, kept as cleanup).
2. DNS storm: added dns object + dns-out outbound + port-53 routing rule + inbound sniffing.
   VERIFIED on device: storm gone (GoLog "operation was canceled" 316→0, connections 288→~30).
3. policy level-8 {handshake:4,connIdle:300,uplinkOnly:1,downlinkOnly:1} + inbound userLevel:8.
4. Connection setup aligned to v2rayNG's CoreVpnService/TProxyService: UnderlyingNetworkMonitor now
   uses requestNetwork() → single default network (+CHANGE_NETWORK_STATE) instead of union-of-all;
   hev config now emits tunnel.ipv4/ipv6 + tcp/udp-read-write-timeout + hev default task stack.
Each was built via CI, installed, verified on device. NONE fixed the large-handshake stall.

## Decisive evidence it is the CORE VERSION
- Same URI, same server, same WiFi, SAME source IP (192.168.0.15) for BOTH our app and v2rayNG.
- v2rayNG (com.v2ray.ang 1.9.46) works perfectly to the SAME server on the SAME WiFi (tun RX +40-59KB,
  pages load, ~6-11 conns). Our client fails (RX trickle, ~30 conns, most Send-Q stuck).
- Every controllable client difference (config, DNS, policy, underlying-net, hev, MTU) is now aligned
  with v2rayNG. The ONLY remaining difference:
    - v2rayNG GoLog: "[Warning] core: Xray 25.3.31 started"
    - our GoLog:     "[Warning] core: Xray 26.5.9 started"
- When one of our handshakes DOES survive, our 26.5.9 splices fine (XtlsFilterTls found tls 1.3 →
  CopyRawConn splice) — so vision itself works; the problem is the handshake packets being DROPPED
  in the path (consistent with DPI acting on the reality/uTLS ClientHello fingerprint of 26.x).

## Our current pin (ADR-0001, docs/adr/0001-xray-core-aar-build-strategy.md)
- Reference recipe: SaeedDev94/Xray v12.3.0, commit 7effa2b8bfac129141526e335775ede916f2c96e (2026-05-23)
- Binding: SaeedDev94/libXray fork submodule at commit f6ce61228b5630f7bcf3c3c9a19d7e1db50b88d1
  — frozen API Test/Start/Stop/Version/Json; the gomobile binding exposes
  registerDialerController(DialerController.protectFd(long)), newXrayRunFromJSONRequest(datDir,json),
  runXrayFromJSON(req), stopXray().
- Xray-core: v26.5.9 (wrapped by the fork). ADR notes fork lags XTLS/libXray upstream (~26.7.11).
- Toolchain: Go 1.26.3, gomobile v0.0.0-20260520154334-..., NDK r29 29.0.14206865, hev v2.15.0.
- Build entry: data/vpn/buildXrayCore.sh (gomobile bind of the libXray submodule). Submodule pin
  asserted in CI (.github/workflows/ci.yml "Assert libXray submodule at pinned SHA").

## The decision (user approved): research, then re-pin Xray-core to v2rayNG's known-good 25.3.31
