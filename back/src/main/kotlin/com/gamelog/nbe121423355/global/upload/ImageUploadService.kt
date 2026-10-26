package com.gamelog.nbe121423355.global.upload

import com.cloudinary.Cloudinary
import com.cloudinary.utils.ObjectUtils
import com.gamelog.nbe121423355.global.exception.ServiceException
import java.io.IOException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
class ImageUploadService(private val cloudinary: Cloudinary) {

    // 이미지 업로드메소드
    fun uploadImage(file: MultipartFile): String? {
        if (file.isEmpty) {
            throw ServiceException("400-4", "업로드할 파일이 없습니다.")
        }
        val contentType = file.contentType
        if (contentType == null || !contentType.startsWith("image/")) {
            throw ServiceException("400-5", "이미지 파일만 업로드할 수 있습니다.")
        }
        return try {
            val result: MutableMap<*, *> =
                cloudinary.uploader().upload(file.bytes, ObjectUtils.emptyMap())
            result["secure_url"] as String?
        } catch (e: IOException) {
            throw ServiceException("500-2", "이미지 업로드에 실패했습니다.", e)
        } catch (e: RuntimeException) {
            throw ServiceException("500-2", "이미지 업로드에 실패했습니다.", e)
        }
    }

    // 이미지 삭제 메소드
    fun deleteImage(imageUrl: String?) {
        val publicId = extractPublicId(imageUrl)
        if (publicId == null) {
            return
        }
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap())
        } catch (e: IOException) {
            log.warn("이미지 삭제 실패: {}", imageUrl, e)
        } catch (e: RuntimeException) {
            log.warn("이미지 삭제 실패: {}", imageUrl, e)
        }
    }

    private fun extractPublicId(imageUrl: String?): String? {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return null
        }
        val uploadIndex = imageUrl.indexOf("/upload/")
        if (uploadIndex == -1) {
            return null
        }
        val afterUpload = imageUrl.substring(uploadIndex + "/upload/".length)
        val parts = afterUpload.split("/", limit = 2)
        val rest = if (parts.size > 1 && parts[0].matches(Regex("v\\d+"))) parts[1] else afterUpload
        val dotIndex = rest.lastIndexOf(".")
        return if (dotIndex == -1) rest else rest.substring(0, dotIndex)
    }

    companion object {
        private val log = LoggerFactory.getLogger(ImageUploadService::class.java)
    }
}
