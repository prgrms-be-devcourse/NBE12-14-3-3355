package com.gamelog.nbe121423355.global.upload

import com.cloudinary.Cloudinary
import com.cloudinary.Uploader
import com.gamelog.nbe121423355.global.exception.ServiceException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.mock.web.MockMultipartFile

// Uploader는 Java 클래스라 인자가 플랫폼 타입이므로 Mockito 매처가 null을 반환해도 NPE가 나지 않음
@ExtendWith(MockitoExtension::class)
class ImageUploadServiceTest {
    @Mock
    private lateinit var cloudinary: Cloudinary

    @Mock
    private lateinit var uploader: Uploader

    private lateinit var imageUploadService: ImageUploadService

    @BeforeEach
    fun setUp() {
        imageUploadService = ImageUploadService(cloudinary)
    }

    @Test
    @DisplayName("uploadImage - 빈 파일이면 Cloudinary 호출 없이 예외")
    fun uploadImage_fail_emptyFile() {
        val emptyFile = MockMultipartFile("file", "empty.jpg", "image/jpeg", ByteArray(0))

        assertThatThrownBy { imageUploadService.uploadImage(emptyFile) }
            .isInstanceOf(ServiceException::class.java)
            .satisfies({ e -> assertThat((e as ServiceException).resultCode).isEqualTo("400-4") })

        verifyNoInteractions(cloudinary)
    }

    @Test
    @DisplayName("uploadImage - 이미지가 아닌 파일이면 Cloudinary 호출 없이 예외")
    fun uploadImage_fail_notImage() {
        val textFile = MockMultipartFile("file", "readme.txt", "text/plain", "hello".toByteArray())

        assertThatThrownBy { imageUploadService.uploadImage(textFile) }
            .isInstanceOf(ServiceException::class.java)
            .satisfies({ e -> assertThat((e as ServiceException).resultCode).isEqualTo("400-5") })

        verifyNoInteractions(cloudinary)
    }

    @Test
    @DisplayName("uploadImage - 성공하면 secure_url 반환")
    fun uploadImage_success() {
        val imageFile = MockMultipartFile("file", "photo.jpg", "image/jpeg", "fake-image-bytes".toByteArray())
        `when`(cloudinary.uploader()).thenReturn(uploader)
        `when`(uploader.upload(any(ByteArray::class.java), anyMap<Any, Any>()))
            .thenReturn(mapOf("secure_url" to "https://res.cloudinary.com/t0pb5sje/image/upload/v1/abc123.jpg"))

        val url = imageUploadService.uploadImage(imageFile)

        assertThat(url).isEqualTo("https://res.cloudinary.com/t0pb5sje/image/upload/v1/abc123.jpg")
    }

    @Test
    @DisplayName("uploadImage - Cloudinary가 예외를 던지면 ServiceException으로 감쌈")
    fun uploadImage_fail_cloudinaryError() {
        val imageFile = MockMultipartFile("file", "photo.jpg", "image/jpeg", "fake-image-bytes".toByteArray())
        `when`(cloudinary.uploader()).thenReturn(uploader)
        `when`(uploader.upload(any(ByteArray::class.java), anyMap<Any, Any>())).thenThrow(RuntimeException("Invalid Signature"))

        assertThatThrownBy { imageUploadService.uploadImage(imageFile) }
            .isInstanceOf(ServiceException::class.java)
            .satisfies({ e -> assertThat((e as ServiceException).resultCode).isEqualTo("500-2") })
    }

    @Test
    @DisplayName("deleteImage - 버전 세그먼트 있는 URL에서 public_id를 추출해 destroy 호출")
    fun deleteImage_success_withVersion() {
        `when`(cloudinary.uploader()).thenReturn(uploader)

        imageUploadService.deleteImage("https://res.cloudinary.com/t0pb5sje/image/upload/v1789972470/nnw0f7uly9mohes2kwws.jpg")

        verify(uploader).destroy(eq("nnw0f7uly9mohes2kwws"), anyMap<Any, Any>())
    }

    @Test
    @DisplayName("deleteImage - 버전 세그먼트 없는 URL도 처리")
    fun deleteImage_success_withoutVersion() {
        `when`(cloudinary.uploader()).thenReturn(uploader)

        imageUploadService.deleteImage("https://res.cloudinary.com/t0pb5sje/image/upload/nnw0f7uly9mohes2kwws.jpg")

        verify(uploader).destroy(eq("nnw0f7uly9mohes2kwws"), anyMap<Any, Any>())
    }

    @Test
    @DisplayName("deleteImage - null이면 Cloudinary 호출 안 함")
    fun deleteImage_null_doesNothing() {
        imageUploadService.deleteImage(null)

        verifyNoInteractions(cloudinary)
    }

    @Test
    @DisplayName("deleteImage - Cloudinary URL 형식이 아니면 호출 안 함")
    fun deleteImage_malformedUrl_doesNothing() {
        imageUploadService.deleteImage("not-a-cloudinary-url")

        verifyNoInteractions(cloudinary)
    }

    @Test
    @DisplayName("deleteImage - destroy가 예외를 던져도 밖으로 전파되지 않음")
    fun deleteImage_swallowsException() {
        `when`(cloudinary.uploader()).thenReturn(uploader)
        `when`(uploader.destroy(anyString(), anyMap<Any, Any>())).thenThrow(RuntimeException("network error"))

        imageUploadService.deleteImage("https://res.cloudinary.com/t0pb5sje/image/upload/v1/abc123.jpg")
    }
}
