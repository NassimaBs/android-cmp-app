package com.sourcepointmeta.cmplibrary

import android.os.Build
import android.util.Log
import java.net.MalformedURLException
import java.net.URL

class SourcePointClientBuilder constructor(accountId: Int, siteName: String, siteId: Int, staging: Boolean) {


    private val DEFAULT_STAGING_MMS_DOMAIN: String = "mms.sp-stage.net"
    private val DEFAULT_MMS_DOMAIN: String = "mms.sp-prod.net"
    private val DEFAULT_INTERNAL_CMP_DOMAIN: String = "cmp.sp-stage.net"
    private val DEFAULT_CMP_DOMAIN: String = "sourcepoint.mgr.consensu.org"
    private val DEFAULT_INTERNAL_IN_APP_MESSAGING_PAGE_DOMAIN: String = "in-app-messaging.pm.cmp.sp-stage.net/v2.0.html"
    private val DEFAULT_IN_APP_MESSAGING_PAGE_DOMAIN: String = "in-app-messaging.pm.sourcepoint.mgr.consensu.org/v3/index.html"

    private var accountId: EncodedParam
    private var site: EncodedParam
    private var siteId: EncodedParam
    private var staging: Boolean
    private var stagingCampaign: Boolean = false
    private var isShowPM: Boolean = false

    private var mmsDomain: String? = null
    private var cmpDomain: String? = null
    private var messageDomain: String? = null

    init {
        this.accountId = EncodedParam("AccountId", accountId.toString())
        this.site = EncodedParam("SiteName", "https://$siteName")
        this.siteId = EncodedParam("siteId", siteId.toString())
        this.staging = staging

    }

    private fun isSafeToHTTPS(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
    }

    private fun protocol(): String {
        return if (isSafeToHTTPS()) "https" else "http";
    }

    private fun setDefaults() {
            if (staging){
                this.messageDomain = DEFAULT_INTERNAL_IN_APP_MESSAGING_PAGE_DOMAIN
                this.mmsDomain = DEFAULT_STAGING_MMS_DOMAIN
                this.cmpDomain = DEFAULT_INTERNAL_CMP_DOMAIN
            }
            else{
                this.messageDomain = DEFAULT_IN_APP_MESSAGING_PAGE_DOMAIN;
                this.mmsDomain = DEFAULT_MMS_DOMAIN;
                this.cmpDomain = DEFAULT_CMP_DOMAIN;
            }
    }

    fun setMmsDomain(mmsDomain: String): SourcePointClientBuilder {
        this.mmsDomain = mmsDomain
        return this
    }

    fun setCmpDomain(cmpDomain: String): SourcePointClientBuilder {
        this.cmpDomain = cmpDomain
        return this
    }

    fun setMessageDomain(messageDomain: String): SourcePointClientBuilder {
        this.messageDomain = messageDomain
        return this
    }

    fun setStagingCampaign(stagingCampaign: Boolean): SourcePointClientBuilder {
        this.stagingCampaign = stagingCampaign
        return this
    }

    fun setShowPM(isShowPm:Boolean): SourcePointClientBuilder{
        this.isShowPM = isShowPm
        return this
    }

    @Throws(ConsentLibException.BuildException::class)
    fun build(): SourcePointClient {
        setDefaults()
        try {
            return SourcePointClient(
                accountId,
                site,
                siteId,
                stagingCampaign,
                isShowPM,
                URL("https", mmsDomain, ""),
                URL("https", cmpDomain, ""),
                URL("https", messageDomain, "")
            )
        } catch (e: MalformedURLException) {
            throw ConsentLibException.BuildException(e.message)
        }
    }
}