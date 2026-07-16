# Issue #111 — one-way tunnel: evidence pack for root-cause

## Symptom
Real tunnel comes up (Xray 26.5.9, hev loads, tun0 up). App→server works (tun0 TX grows);
server→app dead (tun0 RX frozen). Web pages don't load. The SAME vless URI works in v2rayNG
on the SAME phone/network/server (144.31.104.124:443), fully bidirectional.

## Server config URI (works in v2rayNG)
vless://<uuid>@144.31.104.124:443?security=reality&encryption=none&pbk=<pbk>&headerType=&fp=chrome&spx=%2F&type=tcp&flow=xtls-rprx-vision&sni=dl.google.com&sid=227f04

## What was TRIED and FAILED
Hypothesis #1 (WRONG): TUN MTU 8500 → 1500. Verified on device: tun0 MTU is now 1500, but the
black hole PERSISTS unchanged. MTU is NOT the cause. (v2rayNG also uses 1500 and works.)

## Device evidence (our client, MTU now 1500)
- `ss -tno` to 144.31.104.124:443: 288 ESTAB (was 455), every conn Send-Q stuck ~1723-1819 bytes,
  cwnd:1, retrans:6-8, RTO backing off to minutes. TCP 3-way completes but first ~1.7KB data
  segment never ACKed. Local SOCKS loopback (127.0.0.1:10808) perfectly healthy (cwnd:10, Send-Q 0).
- tun0 during browsing: RX +80 bytes, TX +11KB. Return path dead.

## Xray debug GoLog (loglevel=debug) — see golog_debug.txt (5051 lines). Key pattern:
Hundreds of, one per DNS query:
  proxy/socks: client UDP connection from udp:127.0.0.1:XXXXX
  proxy/socks: send packet to udp:1.0.0.1:53 with 43 bytes   (DNS query to our pushed DNS server)
  transport/internet/tcp: dialing TCP to tcp:144.31.104.124:443   (NEW vless conn PER DNS query!)
Then all end in:
  app/proxyman/outbound: failed to process outbound traffic > proxy/vless/outbound:
  failed to find an available destination > common/retry:
  [context canceled dial tcp 144.31.104.124:443: operation was canceled] > all retry attempts failed
Also: transport/internet/udp: failed to handle UDP input > io: read/write on closed pipe
A few real TCP app connects appear (tcp:194.221.250.50:443, 149.154.167.50:443) but relay ~0 data.

## OUR generated Xray config (from XrayConfigBuilder.kt) — 549 bytes, MINIMAL:
{"log":{"loglevel":"debug"},   // temporary, added for diagnostics
 "inbounds":[{"tag":"socks-in","protocol":"socks","listen":"127.0.0.1","port":10808,
              "settings":{"auth":"noauth","udp":true}}],   // NO sniffing
 "outbounds":[{"tag":"proxy-out","protocol":"vless","settings":{"vnext":[{...vision/reality...}]},
              "streamSettings":{"network":"tcp","security":"reality","realitySettings":{...}}}]}
 // NO dns, NO routing, NO sniffing, NO freedom/direct outbound, NO blackhole, single outbound only.
TUN pushes DNS servers 1.1.1.1 / 1.0.0.1 (TunConfig.dnsServers), socks inbound udp:true.

## v2rayNG base template (assets/v2ray_config.json) — the WORKING reference structure:
- inbound socks has `sniffing: {enabled:true, destOverride:["http","tls","quic"]}` and userLevel:8
- outbounds: proxy + `direct`(freedom, sockopt.domainStrategy UseIP) + `block`(blackhole)
- `routing`: {domainStrategy:"AsIs", rules:[...filled at runtime...]}
- `dns`: {hosts:{}, servers:[...filled at runtime...]}
- policy.levels."8" {handshake:4, connIdle:300, uplinkOnly:1, downlinkOnly:1}
v2rayNG needs only ~6 connections to the server; we storm to 288+.

## Question for analysis
Why does the reality handshake / return traffic fail for us but not v2rayNG, both at MTU 1500,
same server? The debug log shows a DNS-driven connection storm where each UDP DNS query opens a
new vless connection that is canceled before its handshake completes. Is the missing dns/routing/
sniffing config (so DNS is NOT proxied per-query as raw UDP) the root cause? What is the MINIMAL,
correct change to XrayConfigBuilder.kt to make our config behave like v2rayNG's?
