package com.sourcepointmeta.cmplibrary

import java.io.UnsupportedEncodingException
import java.net.URLEncoder

class EncodedParam @Throws(ConsentLibException.BuildException::class)
constructor(name: String, value: String) {
    private val value: String

    init {
        this.value = encode(name, value)
    }

    @Throws(ConsentLibException.BuildException::class)
    private fun encode(attrName: String, attrValue: String): String {
        try {
            return URLEncoder.encode(attrValue, "UTF-8")
        } catch (e: UnsupportedEncodingException) {
            throw ConsentLibException.BuildException("Unable to encode $attrName, with the value: $attrValue when instantiating SourcePointClient")
        }
    }

    override fun toString(): String {
        return value
    }
}