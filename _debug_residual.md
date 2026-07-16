# Issue #111 — RESIDUAL analysis (after the DNS-storm fix)

## Progress so far
Fix #1 (MTU 8500→1500): built, installed, verified on-device → did NOT fix. MTU is not it.
Fix #2 (dns/routing/sniffing added — dns-out + port-53 routing rule + sniffing http/tls/quic):
built, installed, verified on-device → **DNS storm ELIMINATED** (GoLog `operation was canceled`
went 316→0, `closed pipe` 311→0; connections to server 288→~32). DNS now resolves internally
(`app/dns: UDP:1.1.1.1:53 got answer ... TypeA -> [...]`, `taking detour [dns-out]`). BUT pages
still do NOT load; return path still largely impaired.

## The RESIDUAL problem (this is what remains)
On-device, our current build (config = dns/routing/sniffing + MTU 1500 + temporary log:debug):
- `ss -tno | grep 144.31.104.124:443`: ~32 ESTAB, MOST with Send-Q stuck at ~1723-1819 bytes,
  cwnd:1, retrans 4-8, RTO backing off. A few drain (Send-Q 25). tun0 RX grows but SLOWLY
  (e.g. 36 KB) while TX races ahead; pages render blank.
- GoLog pattern of WHAT succeeds vs stalls (decisive):
  - SUCCEEDS: `proxy/vless/outbound: tunneling request to tcp:1.0.0.1:853 via 144.31.104.124:443`
    then `XtlsFilterTls found tls client hello! 228` — i.e. the SMALL (228-byte) DoT ClientHellos
    tunnel instantly.
  - STALLS: `proxy/socks: TCP Connect request to tcp:151.101.65.91:443` → `accepted [socks-in >>
    proxy-out]` → then NOTHING (never reaches "tunneling request"). The LARGER HTTPS ClientHellos
    never complete the reality handshake to the server.
  - So: small first-segments get through; large ones black-hole. Only ~2 of ~32 handshakes succeed.
- Device also uses Android Private DNS = DoT to 1.1.1.1:853 (TCP 853, NOT caught by our port-53
  rule) and Chrome uses DoH to chrome.cloudflare-dns.com; both go via proxy-out. Xray logs
  `proxy/dns: rejected type TypeHTTPS query` (queryStrategy=UseIPv4 rejects HTTPS/SVCB records).

## The CONTROL (rules out network/underlying-net)
- v2rayNG (com.v2ray.ang 1.9.46), SAME phone, SAME server 144.31.104.124, tested on BOTH WiFi and
  cellular: fully bidirectional (tun0 RX +41-59 KB), pages load. Only ~6-11 connections.
- CRUCIAL: BOTH our app AND v2rayNG source their server connections from the SAME IP 192.168.0.15
  (WiFi), even when cellular is the "primary" default network. So underlying-network / cellular
  path MTU is NOT the difference — same path for both.
- v2rayNG ALSO shows a FEW Send-Q ~1788 stuck connections, but it ALSO has draining ones and
  recovers; its handshakes mostly succeed instantly. Ours mostly stall permanently and accumulate.

## The core mystery for you to crack
Same source IP (192.168.0.15), same dest (144.31.104.124:443), same ~1788-byte first data segment,
same WiFi path — yet v2rayNG's reality/vision handshakes succeed and ours mostly stall (first data
segment never ACKed at TCP level → not a reality-app rejection, a network-level drop). When ours
DOES work it's instant (small segments); large segments never get through. It looks like a
large-packet / burst-sensitive drop that v2rayNG avoids.

Candidate differences between our minimal config and v2rayNG's (v2ray_config.json template):
- v2rayNG sets `policy.levels."8" { handshake:4, connIdle:300, uplinkOnly:1, downlinkOnly:1 }` and
  socks inbound `userLevel:8`; ours has NO policy / no userLevel (default level 0). uplinkOnly/
  downlinkOnly:1 aggressively reaps half-closed conns — could stop our stuck conns accumulating.
- v2rayNG sets `mux { enabled:false }` explicitly (we omit it; default is disabled anyway).
- v2rayNG proxy outbound may set `sockopt` (tcpMaxSeg / tcpFastOpen / mark)? Verify from its source.
- Xray-core version: ours = 26.5.9 (SaeedDev94 fork). v2rayNG 1.9.46 bundles ??? — could differ in
  vision padding / reality ClientHello. Determine v2rayNG's bundled Xray-core version if possible.
- vision padding inflates a small ClientHello to ~1788 bytes; is there a client-side knob
  (e.g. no padding, or flow without vision) that changes first-segment size? (We must keep vision —
  the server requires flow=xtls-rprx-vision.)

## Files
- Config builder: C:\YarokovistY\Android\VPNis\data\vpn\src\main\kotlin\org\yarokovisty\vpnis\data\vpn\XrayConfigBuilder.kt
- Fresh residual GoLog: C:\YarokovistY\Android\VPNis\golog_residual.txt (1440 lines)
- Earlier storm GoLog: C:\YarokovistY\Android\VPNis\golog_debug.txt
- TunConfig (DNS servers pushed, MTU): C:\YarokovistY\Android\VPNis\data\vpn\...\TunConfig.kt

## Questions
1. Definitive root cause of the RESIDUAL large-segment/handshake stall (network-level drop of the
   first ~1.7KB segment that v2rayNG avoids on the identical path). Is it burst/connection-count/
   rate, the missing `policy` (uplinkOnly/downlinkOnly reaping), a sockopt v2rayNG sets, an
   MSS/segment-size issue, or a core-version vision difference? Use golog_residual.txt to support.
2. The MINIMAL config change to XrayConfigBuilder to make our handshakes succeed like v2rayNG's.
   If it's `policy`+`userLevel`, give the exact JSON. If it's sockopt (e.g. tcpMaxSeg to clamp the
   segment below the path MTU), give exact keys/values and explain. Prefer the smallest change with
   the strongest causal link to the evidence.
