package com.gamelog.nbe121423355.global.upload

import com.cloudinary.Cloudinary
import com.cloudinary.utils.ObjectUtils
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CloudinaryConfig(private val cloudinaryProperties: CloudinaryProperties) {

    @Bean
    fun cloudinary(): Cloudinary {
        return Cloudinary(
            ObjectUtils.asMap(
                "cloud_name",
                cloudinaryProperties.cloudName,
                "api_key",
                cloudinaryProperties.apiKey,
                "api_secret",
                cloudinaryProperties.apiSecret,
            )
        )
    }
}
