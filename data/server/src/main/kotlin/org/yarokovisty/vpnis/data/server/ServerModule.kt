package org.yarokovisty.vpnis.data.server

import org.koin.core.module.Module
import org.koin.dsl.module
import org.yarokovisty.vpnis.core.domain.repository.ServerRepository

/**
 * Koin module that binds [ServerRepositoryImpl] as the app-wide singleton for
 * [ServerRepository].
 *
 * This module provides the real in-memory repository pre-seeded with [DEFAULT_SERVER]
 * (FR-50, SRS §5.6). It replaces the fake ServerRepository binding that was previously
 * part of `:data:fake`'s `fakeVpnModule`. The fake is now available only for UI tests
 * (issue #58); the release graph contains exactly one [ServerRepository] binding — this one.
 *
 * When Room-backed persistence is added (Servers feature, post-MVP), this module will be
 * replaced or extended without touching `:feature:home` or any other consumer.
 */
public val serverModule: Module = module {
    single<ServerRepository> { ServerRepositoryImpl() }
}
