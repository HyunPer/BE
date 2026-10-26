package com.pleos.backend.asset

import com.pleos.backend.common.ApiException
import com.pleos.backend.common.notFound
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

interface AssetRepository : JpaRepository<Asset, UUID>

data class AssetResponse(
    val id: UUID,
    val url: String,
    val contentType: String,
    val sizeBytes: Long,
    val createdAt: Instant,
)

fun Asset.toResponse() = AssetResponse(requireId(), assetUrl(requireId()), contentType, sizeBytes, createdAt)

class StoredFile(val contentType: String, val path: Path)

/**
 * 이미지를 로컬 디스크(pleos.files.dir)에 저장한다. 운영에서는 S3 같은 오브젝트 스토리지로 바꾼다.
 * 확장자·헤더가 아니라 파일 앞부분(매직 넘버)으로 형식을 판정한다.
 * GIF는 받지 않는다: 움직이는 이미지는 운전 중 주의를 끌 수 있다.
 */
@Service
@Transactional(readOnly = true)
class AssetService(
    private val repository: AssetRepository,
    @Value("\${pleos.files.dir}") dir: String,
    @Value("\${pleos.files.max-bytes}") private val maxBytes: Long,
) {
    private val root: Path = Path.of(dir).toAbsolutePath().also { Files.createDirectories(it) }

    @Transactional
    fun upload(ownerId: UUID, file: MultipartFile): AssetResponse {
        if (file.isEmpty) throw badRequest("빈 파일입니다")
        if (file.size > maxBytes) throw badRequest("이미지는 ${maxBytes / 1024 / 1024}MB 이하만 올릴 수 있습니다")
        val bytes = file.bytes
        val contentType = detectImageType(bytes) ?: throw badRequest("PNG, JPEG, WEBP 이미지만 올릴 수 있습니다")
        val asset = repository.save(Asset(ownerId = ownerId, contentType = contentType, sizeBytes = bytes.size.toLong()))
        Files.write(pathOf(asset.requireId()), bytes)
        return asset.toResponse()
    }

    fun load(id: UUID): StoredFile {
        val asset = repository.findById(id).orElseThrow { notFound("파일을 찾을 수 없습니다: $id") }
        val path = pathOf(id)
        if (!Files.exists(path)) throw notFound("파일을 찾을 수 없습니다: $id")
        return StoredFile(asset.contentType, path)
    }

    fun exists(id: UUID): Boolean = repository.existsById(id)

    private fun pathOf(id: UUID): Path = root.resolve(id.toString())

    private fun badRequest(detail: String) = ApiException(HttpStatus.BAD_REQUEST, detail)

    companion object {
        fun detectImageType(bytes: ByteArray): String? = when {
            bytes.startsWith(0x89, 0x50, 0x4E, 0x47) -> "image/png"
            bytes.startsWith(0xFF, 0xD8, 0xFF) -> "image/jpeg"
            bytes.size >= 12 && bytes.startsWith(0x52, 0x49, 0x46, 0x46) &&
                String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
            else -> null
        }

        private fun ByteArray.startsWith(vararg prefix: Int): Boolean =
            size >= prefix.size && prefix.indices.all { this[it] == prefix[it].toByte() }
    }
}
