package com.sourcepointmeta.cmplibrary

import android.text.TextUtils
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.loopj.android.http.AsyncHttpClient
import com.loopj.android.http.JsonHttpResponseHandler
import cz.msebera.android.httpclient.Header
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.util.*

open class SourcePointClient(
    private var accountId: EncodedParam,
    private var site: EncodedParam, private var siteId: EncodedParam,
    private var stagingCampaign: Boolean, private var isShowPM: Boolean,
    private var mmsUrl: URL, private var cmpUrl: URL, private var msgUrl: URL
) {
    private val LOG_TAG = "SOURCE_POINT_CLIENT"

    private var http = AsyncHttpClient()


    private fun gdprStatusUrl(): String {
        return this.cmpUrl.toString() + "/consent/v2/gdpr-status"
    }

    private fun customConsentsUrl(
        consentUUID: String?,
        euConsent: String?,
        siteId: String,
        vendorIds: Array<String?>
    ): String {
        val consentParam = consentUUID ?: "[CONSENT_UUID]"
        val euconsentParam = euConsent ?: "[EUCONSENT]"
        val customVendorIdString = URLEncoder.encode(TextUtils.join(",", vendorIds))

        return cmpUrl.toString() + "/consent/v2/" + siteId + "/custom-vendors?" +
                "customVendorIds=" + customVendorIdString +
                "&consentUUID=" + consentParam +
                "&euconsent=" + euconsentParam
    }

    fun messageUrl(targetingParams: EncodedParam, authId: EncodedParam?, pmId: EncodedParam): String {
        val params = HashSet<String>()
        params.add("_sp_accountId=$accountId")
        params.add("_sp_siteId=$siteId")
        params.add("_sp_siteHref=$site")
        params.add("_sp_mms_Domain=$mmsUrl")
        params.add("_sp_cmp_origin=$cmpUrl")
        params.add("_sp_targetingParams=$targetingParams")
        params.add("_sp_env=" + if (stagingCampaign) "stage" else "public")
        params.add("_sp_PMId=$pmId")
        params.add("_sp_runMessaging=" + !isShowPM)
        params.add("_sp_showPM=$isShowPM")

        if (authId != null) {
            params.add("_sp_authId=$authId")
        }

        return msgUrl.toString() + "?" + TextUtils.join("&", params)
    }

    @VisibleForTesting
    fun setHttpDummy(httpClient: AsyncHttpClient) {
        http = httpClient
    }

    internal open inner class ResponseHandler(var url: String, var onLoadComplete: ConsentLib.OnLoadComplete) :
        JsonHttpResponseHandler() {

        override fun onFailure(
            statusCode: Int,
            headers: Array<Header>?,
            throwable: Throwable,
            errorResponse: JSONObject?
        ) {
            Log.d(LOG_TAG, "Failed to load resource $url due to $statusCode: $errorResponse")
            onLoadComplete.onFailure(ConsentLibException(throwable.message))
        }

        override fun onFailure(
            statusCode: Int,
            headers: Array<Header>?,
            responseString: String?,
            throwable: Throwable
        ) {
            Log.d(LOG_TAG, "Failed to load resource $url due to $statusCode: $responseString")
            onLoadComplete.onFailure(ConsentLibException(throwable.message))
        }
    }

    fun getGDPRStatus(onLoadComplete: ConsentLib.OnLoadComplete) {
        val url = gdprStatusUrl()
        http.get(url, object : ResponseHandler(url, onLoadComplete) {
            override fun onSuccess(statusCode: Int, headers: Array<Header>?, response: JSONObject?) {
                try {
                    onLoadComplete.onSuccess(response!!.getString("gdprApplies"))
                } catch (e: JSONException) {
                    onFailure(statusCode, headers, e, response)
                }
            }

            override fun onFailure(
                statusCode: Int,
                headers: Array<Header>?,
                responseString: String?,
                throwable: Throwable
            ) {
                Log.d(LOG_TAG, "Failed to load resource $url due to $statusCode: $responseString")
                onLoadComplete.onFailure(ConsentLibException(throwable.message))
            }

            override fun onFailure(
                statusCode: Int,
                headers: Array<Header>?,
                throwable: Throwable,
                errorResponse: JSONArray?
            ) {
                Log.d(LOG_TAG, "Failed to load resource $url due to $statusCode: $errorResponse")
                onLoadComplete.onFailure(ConsentLibException(throwable.message))
            }
        })
    }

    @Throws(JSONException::class)
    private fun getConsentFromResponse(response: JSONObject, type: String): HashSet<Consent> {
        val consentsJSON = response.getJSONArray(type)
        val consents = HashSet<Consent>()
        for (i in 0 until consentsJSON.length()) {
            when (type) {
                "consentedVendors" -> {
                    consents.add(
                        CustomVendorConsent(
                            consentsJSON.getJSONObject(i).getString("_id"),
                            consentsJSON.getJSONObject(i).getString("name"),
                            type
                        )
                    )
                }
                "consentedPurposes" -> {
                    consents.add(
                        CustomPurposeConsent(
                            consentsJSON.getJSONObject(i).getString("_id"),
                            consentsJSON.getJSONObject(i).getString("name"),
                            type
                        )
                    )
                }
            }
        }
        return consents
    }

    fun getCustomConsents(
        consentUUID: String,
        euConsent: String,
        siteId: String,
        vendorIds: Array<String?>,
        onLoadComplete: ConsentLib.OnLoadComplete
    ) {
        val url = customConsentsUrl(consentUUID, euConsent, siteId, vendorIds)
        http.get(url, object : ResponseHandler(url, onLoadComplete) {
            override fun onSuccess(statusCode: Int, headers: Array<Header>?, response: JSONObject?) {
                val consents = HashSet<Consent>()

                try {
                    consents.addAll(getConsentFromResponse(response!!, "consentedVendors"))
                    consents.addAll(getConsentFromResponse(response, "consentedPurposes"))
                    onLoadComplete.onSuccess(consents)
                } catch (e: JSONException) {
                    onFailure(statusCode, headers, e, response)
                }
            }

            override fun onFailure(
                statusCode: Int,
                headers: Array<Header>?,
                responseString: String?,
                throwable: Throwable
            ) {
                Log.d(LOG_TAG, "Failed to load resource $url due to $statusCode: $responseString")
                onLoadComplete.onFailure(ConsentLibException(throwable.message))
            }

            override fun onFailure(
                statusCode: Int,
                headers: Array<Header>?,
                throwable: Throwable,
                errorResponse: JSONArray?
            ) {
                Log.d(LOG_TAG, "Failed to load resource $url due to $statusCode: $errorResponse")
                onLoadComplete.onFailure(ConsentLibException(throwable.message))
            }
        })
    }

}