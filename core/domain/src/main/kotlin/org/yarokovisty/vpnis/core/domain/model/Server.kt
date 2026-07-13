package org.yarokovisty.vpnis.core.domain.model

/**
 * Represents a VPN server entry in the domain.
 *
 * [config] is an opaque connection descriptor (e.g. a VLESS/Reality URI string).
 * The domain layer does NOT parse or validate its contents — that responsibility
 * belongs to :data:vpn, which owns the protocol-specific parsing logic.
 */
public data class Server(val id: ServerId, val name: String, val config: String)
