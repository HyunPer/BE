package com.pleos.backend.asset

import com.pleos.backend.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

/**
 * 테마에 쓰는 이미지(배경·캐릭터). 한 번 올리면 바뀌지 않는다.
 * 테마를 공유받은 사람도 같은 이미지를 참조하므로 수정·삭제 API는 두지 않는다.
 */
@Entity
@Table(name = "assets")
class Asset(
    @Column(name = "owner_id", nullable = false, updatable = false)
    val ownerId: UUID,

    @Column(nullable = false, updatable = false, length = 20)
    val contentType: String,

    @Column(nullable = false, updatable = false)
    val sizeBytes: Long,
) : BaseEntity()

/** 클라이언트는 기본 주소 뒤에 붙여서 내려받는다. 예: http://10.0.2.2:8090/files/{id} */
fun assetUrl(id: UUID) = "/files/$id"
