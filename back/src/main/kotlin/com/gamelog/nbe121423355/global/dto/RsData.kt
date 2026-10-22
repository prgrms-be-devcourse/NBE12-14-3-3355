package com.gamelog.nbe121423355.global.dto

import com.fasterxml.jackson.annotation.JsonIgnore

class RsData<T> {
    val resultCode: String

    val msg: String?

    val data: T?

    constructor(resultCode: String, msg: String?) {
        this.resultCode = resultCode
        this.msg = msg
        this.data = null
    }

    @get:JsonIgnore
    val statusCode: Int
        get() = java.lang.Integer.parseInt(resultCode.split("-")[0])

    constructor(resultCode: String, msg: String?, data: T?) {
        this.resultCode = resultCode
        this.msg = msg
        this.data = data
    }
}
