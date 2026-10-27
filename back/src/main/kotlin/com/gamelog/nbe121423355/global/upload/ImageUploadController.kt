package com.gamelog.nbe121423355.global.upload

import com.gamelog.nbe121423355.global.dto.RsData
import com.gamelog.nbe121423355.global.security.SecurityUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.SchemaProperty
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/v1/uploads")
@Tag(name = "image-upload-controller", description = "이미지 업로드 API")
class ImageUploadController(private val imageUploadService: ImageUploadService) {
    @PostMapping("/images")
    @Operation(
        summary = "이미지 업로드",
        description = "로그인이 필요합니다. multipart/form-data의 file 필드로 빈 파일이 아닌 이미지를 전송합니다. Content-Type이 image/로 시작해야 하며 기본 파일·요청 크기 제한은 각각 5MB입니다. 성공하면 data에 접근 가능한 이미지 URL 문자열을 반환합니다.",
        security = [SecurityRequirement(name = "bearerAuth")],
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = [Content(
                mediaType = "multipart/form-data",
                schema = Schema(type = "object", requiredProperties = ["file"]),
                schemaProperties = [SchemaProperty(name = "file", schema = Schema(type = "string", format = "binary"))],
            )],
        ),
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "200-1: 이미지 URL 반환", useReturnTypeSchema = true),
        ApiResponse(responseCode = "400", description = "400-3: 업로드 크기 초과 / 400-4: 빈 파일 / 400-5: 이미지가 아닌 파일", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "401", description = "401-1: 로그인 필요", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "500", description = "500-2: 외부 이미지 저장소 업로드 실패", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    fun uploadImage(
        @Parameter(description = "multipart/form-data의 필수 이미지 파일. 기본 제한 5MB", required = true)
        @RequestParam("file")
        file: MultipartFile,
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser?,
    ): RsData<String> {
        val url = imageUploadService.uploadImage(file)
        return RsData("200-1", "이미지를 업로드했습니다.", url)
    }
}
