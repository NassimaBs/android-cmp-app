package com.sourcepointmeta.cmplibrary

open class ConsentLibException : Exception {

    constructor() : super() {}
    constructor(message: String?) : super(message) {}

    class BuildException internal constructor(message: String?) :
        ConsentLibException("Error during ConsentLib build: $message")

    class NoInternetConnectionException : ConsentLibException()
    class ApiException internal constructor(message: String) : ConsentLibException(message)
}