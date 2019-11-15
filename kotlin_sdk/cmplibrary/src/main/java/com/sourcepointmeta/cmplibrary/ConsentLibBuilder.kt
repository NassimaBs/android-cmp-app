package com.sourcepointmeta.cmplibrary

import android.app.Activity
import android.os.Build
import android.view.ViewGroup
import org.json.JSONException
import org.json.JSONObject

open class ConsentLibBuilder constructor(
    var accountId: Int,
    var siteName: String,
    var siteId: Int,
    var pmId: String,
    var activity: Activity?
) {
    private val targetingParams = JSONObject()
    internal var cmpDomain: String = ""
    internal var msgDomain: String = ""
    internal var mmsDomain: String = ""
    internal var page: String = ""
    var viewGroup: ViewGroup? = null
    internal var staging: Boolean = false
    internal var stagingCampaign: Boolean = false
    private var newPM: Boolean = false
    var isShowPM: Boolean = false
    internal var targetingParamsString: EncodedParam? = null
    internal var authId: EncodedParam? = null
    var defaultMessageTimeOut: Long = 10000
    var onErrorOccurred: ConsentLib.Callback? = null
    var onConsentReady: ConsentLib.Callback? = null
    var onMessageReady: ConsentLib.Callback? = null
    var onMessageChoiceSelect: ConsentLib.Callback? = null

    init {
        val noOpCallback = object : ConsentLib.Callback {
            override fun run(c: ConsentLib) {}
        }
        onMessageReady = noOpCallback
        onErrorOccurred = onMessageReady
        onConsentReady = onErrorOccurred
        onMessageChoiceSelect = onConsentReady
    }


    fun setPage(p: String): ConsentLibBuilder {
        page = p
        return this
    }

    fun setViewGroup(v: ViewGroup): ConsentLibBuilder {
        viewGroup = v
        return this
    }

    fun setOnMessageChoiceSelect(c: ConsentLib.Callback): ConsentLibBuilder {
        onMessageChoiceSelect = c
        return this
    }

    fun setOnConsentReady(c: ConsentLib.Callback): ConsentLibBuilder {
        onConsentReady = c
        return this
    }

    fun setOnMessageReady(callback: ConsentLib.Callback): ConsentLibBuilder {
        onMessageReady = callback
        return this
    }

    fun setOnErrorOccurred(callback: ConsentLib.Callback): ConsentLibBuilder {
        onErrorOccurred = callback
        return this
    }

    fun setStage(st: Boolean): ConsentLibBuilder {
        stagingCampaign = st
        return this
    }

    fun setInternalStage(st: Boolean): ConsentLibBuilder {
        staging = st
        return this
    }

    fun enableNewPM(newPM: Boolean): ConsentLibBuilder {
        this.newPM = newPM
        return this
    }

    fun setInAppMessagePageUrl(inAppMessageUrl: String): ConsentLibBuilder {
        msgDomain = inAppMessageUrl
        return this
    }

    fun setMmsDomain(mmsDomain: String): ConsentLibBuilder {
        this.mmsDomain = mmsDomain
        return this
    }

    fun setCmpDomain(cmpDomain: String): ConsentLibBuilder {
        this.cmpDomain = cmpDomain
        return this
    }

    @Throws(ConsentLibException.BuildException::class)
    fun setAuthId(authId: String): ConsentLibBuilder {
        this.authId = EncodedParam("authId", authId)
        return this
    }

    fun setShowPM(isUserTriggered: Boolean): ConsentLibBuilder {
        this.isShowPM = isUserTriggered
        return this
    }

    @Throws(ConsentLibException.BuildException::class)
    fun setTargetingParam(key: String, `val`: Int?): ConsentLibBuilder {
        return setTargetingParam(key, `val` as Any)
    }

    @Throws(ConsentLibException.BuildException::class)
    fun setTargetingParam(key: String, `val`: String): ConsentLibBuilder {
        return setTargetingParam(key, `val` as Any)
    }

    @Throws(ConsentLibException.BuildException::class)
    private fun setTargetingParam(key: String, `val`: Any): ConsentLibBuilder {
        try {
            this.targetingParams.put(key, `val`)
        } catch (e: JSONException) {
            throw ConsentLibException.BuildException("error parsing targeting param, key: $key value: $`val`")
        }

        return this
    }

    @Throws(ConsentLibException::class)
    private fun setTargetingParamsString() {
        targetingParamsString = EncodedParam("targetingParams", targetingParams.toString())
    }

    private fun sdkNotSupported(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT
    }

    @Throws(ConsentLibException::class)
    fun build(): ConsentLib {
        if (sdkNotSupported()) {
            throw ConsentLibException.BuildException(
                "ConsentLib supports only API level 19 and above.\n" + "See https://github.com/SourcePointUSA/android-cmp-app/issues/25 for more information."
            )
        }

        try {
            setTargetingParamsString()
        } catch (e: ConsentLibException) {
            this.activity = null // release reference to activity
            throw ConsentLibException.BuildException(e.message)

        }

        return ConsentLib(this)
    }

    fun setMessageTimeOut(milliSecond: Long): ConsentLibBuilder {
        this.defaultMessageTimeOut = milliSecond
        return this
    }
}