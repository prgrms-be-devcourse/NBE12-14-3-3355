package com.gamelog.nbe121423355.global.dto

import com.fasterxml.jackson.annotation.JsonIgnore

class RsData<T>(
    val resultCode: String,
    val msg: String?,
    val data: T? = null,
) {
    @get:JsonIgnore
    val statusCode: Int
        get() = resultCode.split("-")[0].toInt()
}
