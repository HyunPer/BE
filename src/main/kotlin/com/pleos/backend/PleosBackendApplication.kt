package com.pleos.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PleosBackendApplication

fun main(args: Array<String>) {
    runApplication<PleosBackendApplication>(*args)
}
