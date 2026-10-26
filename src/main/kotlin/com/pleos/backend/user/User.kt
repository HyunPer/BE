package com.pleos.backend.user

import com.pleos.backend.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * 폰 앱 사용자. MVP에서는 비밀번호 없이 첫 실행 때 닉네임으로 가입하고, 받은 토큰을 기기에 저장해 쓴다.
 */
@Entity
@Table(name = "users")
class User(
    @Column(nullable = false, length = 20)
    var nickname: String,

    @Column(nullable = false, unique = true, length = 64)
    var tokenHash: String,
) : BaseEntity()
