package org.yarokovisty.vpnis.data.server

import org.yarokovisty.vpnis.core.domain.model.Server
import org.yarokovisty.vpnis.core.domain.model.ServerId

/**
 * The hardcoded operator default server shipped with the app (FR-50, SRS §5.6).
 *
 * This entry ensures a new user can one-tap connect on first launch — the server list
 * is never empty out-of-the-box. Real credentials and a production config URI will be
 * provisioned by the operator at build time (or via remote config in a future milestone)
 * and will replace this placeholder.
 *
 * The [Server.config] string is an opaque connection descriptor consumed exclusively by
 * `:data:vpn`. The domain layer (and this module) treat it as a plain [String] and never
 * parse or validate its contents.
 */
public val DEFAULT_SERVER: Server = Server(
    id = ServerId("vpnis-default-nl-1"),
    name = "Нидерланды · Амстердам",
    config = "vless://00000000-0000-0000-0000-000000000000@nl1.vpnis.net:443" +
        "?type=tcp&security=reality&pbk=PLACEHOLDER_PUBLIC_KEY" +
        "&fp=chrome&sni=www.google.com&sid=PLACEHOLDER_SHORT_ID" +
        "#VPNis%20NL",
)
