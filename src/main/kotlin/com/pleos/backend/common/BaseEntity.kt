package com.pleos.backend.common

import jakarta.persistence.Column
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import java.time.Instant
import java.util.UUID

/** 모든 엔티티 공통: UUID 식별자와 생성·수정 시각. */
@MappedSuperclass
abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null
        protected set

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.EPOCH
        protected set

    @Column(nullable = false)
    var updatedAt: Instant = Instant.EPOCH
        protected set

    fun requireId(): UUID = checkNotNull(id) { "저장되지 않은 엔티티입니다: ${javaClass.simpleName}" }

    @PrePersist
    fun onCreate() {
        val now = Instant.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }
}
