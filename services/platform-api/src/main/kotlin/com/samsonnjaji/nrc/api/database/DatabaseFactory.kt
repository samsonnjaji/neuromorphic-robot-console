package com.samsonnjaji.nrc.api.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

fun createNrcDataSource(
    jdbcUrl: String,
    username: String,
    password: String
): HikariDataSource = HikariDataSource(
    HikariConfig().apply {
        this.jdbcUrl = jdbcUrl
        this.username = username
        this.password = password
        poolName = "nrc-platform-api"
        maximumPoolSize = 10
        minimumIdle = 1
        connectionTimeout = 30_000
        validationTimeout = 5_000
        initializationFailTimeout = 10_000
    }
)
