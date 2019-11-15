package com.sourcepointmeta.cmplibrary

import android.app.Activity
import android.content.ContentValues.TAG
import android.content.SharedPreferences
import android.os.CountDownTimer
import android.preference.PreferenceManager
import android.util.Log
import android.view.ViewGroup
import com.iab.gdpr_android.consent.VendorConsent
import com.iab.gdpr_android.consent.VendorConsentDecoder
import java.util.HashSet

open class ConsentLib {
    private val TAG = "ConsentLib"

    val IAB_CONSENT_CMP_PRESENT = "IABConsent_CMPPresent"
    val IAB_CONSENT_SUBJECT_TO_GDPR = "IABConsent_SubjectToGDPR"
    val IAB_CONSENT_CONSENT_STRING = "IABConsent_ConsentString"
    val IAB_CONSENT_PARSED_PURPOSE_CONSENTS = "IABConsent_ParsedPurposeConsents"
    val IAB_CONSENT_PARSED_VENDOR_CONSENTS = "IABConsent_ParsedVendorConsents"
    val EU_CONSENT_KEY = "euconsent"
    val CONSENT_UUID_KEY = "consentUUID"

    enum class DebugLevel {
        DEBUG, OFF
    }

    enum class MESSAGE_OPTIONS {
        SHOW_PRIVACY_MANAGER,
        UNKNOWN
    }

    var euconsent: String? = null
    var consentUUID: String? = null

    private val MAX_PURPOSE_ID = 24
    var choiceType: MESSAGE_OPTIONS? = null
    var error: ConsentLibException? = null
    private val SP_PREFIX = "_sp_"
    private val SP_SITE_ID = SP_PREFIX + "site_id"
    private val CUSTOM_CONSENTS_KEY = SP_PREFIX + "_custom_consents"

    private var activity: Activity? = null
    private var siteName: String? = null
    private var accountId: Int? = null
    private var siteId: Int? = null
    private var viewGroup: ViewGroup? = null
    private var onMessageChoiceSelect: Callback? = null
    private var onConsentReady: Callback? = null
    private var onErrorOccurred: Callback? = null
    private var onMessageReady: Callback? = null
    private var encodedTargetingParams: EncodedParam? = null
    private var encodedAuthId: EncodedParam? = null
    private var encodedPMId: EncodedParam? = null
    private var weOwnTheView: Boolean = false
    private var isShowPM: Boolean = false

    //default time out changes
    private var onMessageReadyCalled = false
    private var defaultMessageTimeOut: Long? = null

    private var mCountDownTimer: CountDownTimer? = null

    private var sourcePoint: SourcePointClient

    private var sharedPref: SharedPreferences

    var webView: ConsentWebView? = null


    interface Callback {
        fun run(c: ConsentLib)
    }

    interface OnLoadComplete {
        fun onSuccess(result: Any)

        fun onFailure(exception: ConsentLibException) {
            Log.d(TAG, "default implementation of onFailure, did you forget to override onFailure ?")
            exception.printStackTrace()
        }
    }

    @Throws(ConsentLibException.BuildException::class)
    constructor(b: ConsentLibBuilder) {
        activity = b.activity
        siteName = b.siteName
        accountId = b.accountId
        siteId = b.siteId
        encodedPMId = EncodedParam("_sp_PMId", b.pmId)
        isShowPM = b.isShowPM
        encodedAuthId = b.authId
        onMessageChoiceSelect = b.onMessageChoiceSelect
        onConsentReady = b.onConsentReady
        onErrorOccurred = b.onErrorOccurred
        onMessageReady = b.onMessageReady
        encodedTargetingParams = b.targetingParamsString
        viewGroup = b.viewGroup

        weOwnTheView = viewGroup != null
        // configurable time out
        defaultMessageTimeOut = b.defaultMessageTimeOut

        sourcePoint = SourcePointClientBuilder(b.accountId, b.siteName + "/" + b.page, siteId!!, b.staging)
            .setStagingCampaign(b.stagingCampaign)
            .setShowPM(b.isShowPM)
            .setCmpDomain(b.cmpDomain)
            .setMessageDomain(b.msgDomain)
            .setMmsDomain(b.mmsDomain)
            .build()

        // read consent from/store consent to default shared preferences
        // per gdpr framework: https://github.com/InteractiveAdvertisingBureau/GDPR-Transparency-and-Consent-Framework/blob/852cf086fdac6d89097fdec7c948e14a2121ca0e/In-App%20Reference/Android/app/src/main/java/com/smaato/soma/cmpconsenttooldemoapp/cmpconsenttool/storage/CMPStorage.java
        sharedPref = PreferenceManager.getDefaultSharedPreferences(activity)
        euconsent = sharedPref.getString(EU_CONSENT_KEY, null)
        consentUUID = sharedPref.getString(CONSENT_UUID_KEY, null)

        webView = buildWebView()
    }

    private fun buildWebView(): ConsentWebView {
        return object : ConsentWebView(this.activity, defaultMessageTimeOut!!, isShowPM) {
            private fun isDefined(s: String?): Boolean {
                return s != null && s != "undefined" && s.isNotEmpty()
            }

            override fun onMessageReady() {
                onMessageReadyCalled = true
                Log.d("msgReady", "called")
                if (mCountDownTimer != null) mCountDownTimer!!.cancel()
                runOnLiveActivityUIThread { this@ConsentLib.onMessageReady!!.run(this@ConsentLib) }
                displayWebViewIfNeeded()
            }

            override fun onErrorOccurred(error: ConsentLibException) {
                this@ConsentLib.error = error
                clearAllConsentData()
                runOnLiveActivityUIThread { this@ConsentLib.onErrorOccurred!!.run(this@ConsentLib) }
                this@ConsentLib.finish()
            }

            override fun onConsentReady(euConsent: String, consentUUID: String) {
                val editor = sharedPref.edit()
                if (isDefined(euConsent)) {
                    this@ConsentLib.euconsent = euConsent
                    editor.putString(EU_CONSENT_KEY, euConsent)
                }
                if (isDefined(consentUUID)) {
                    this@ConsentLib.consentUUID = consentUUID
                    editor.putString(CONSENT_UUID_KEY, consentUUID)
                    Log.d("Consnet UUID = ", consentUUID)
                }
                if (isDefined(euConsent) && isDefined(consentUUID)) {
                    editor.apply()
                    setIABVars(euConsent)
                }
                this@ConsentLib.finish()
            }
            override fun onMessageChoiceSelect(choiceType: Int, choiceId: Int) {
                Log.d(TAG, "onMessageChoiceSelect: choiceId:" + choiceId + "choiceType: " + choiceType)

                when (choiceType) {
                    12 -> this@ConsentLib.choiceType = MESSAGE_OPTIONS.SHOW_PRIVACY_MANAGER
                    else -> this@ConsentLib.choiceType = MESSAGE_OPTIONS.UNKNOWN
                }
                runOnLiveActivityUIThread { this@ConsentLib.onMessageChoiceSelect!!.run(this@ConsentLib) }
            }
        }
    }

    /**
     * Communicates with SourcePoint to load the message. It all happens in the background and the WebView
     * will only show after the message is ready to be displayed (received data from SourcePoint).
     * The Following keys should will be available in the shared preferences storage after this method
     * is called:
     *
     *  * [ConsentLib.IAB_CONSENT_CMP_PRESENT]
     *  * [ConsentLib.IAB_CONSENT_SUBJECT_TO_GDPR]
     *
     *
     * @throws ConsentLibException.NoInternetConnectionException - thrown if the device has lost connection either prior or while interacting with ConsentLib
     */
    @Throws(ConsentLibException.NoInternetConnectionException::class)
    fun run() {
        onMessageReadyCalled = false
        if (webView == null) {
            webView = buildWebView()
        }
        webView!!.loadMessage(sourcePoint.messageUrl(encodedTargetingParams!!, encodedAuthId, encodedPMId!!))
        mCountDownTimer = getTimer(defaultMessageTimeOut!!)
        mCountDownTimer!!.start()
        setSharedPreference(IAB_CONSENT_CMP_PRESENT, true)
        setSubjectToGDPR()
    }

    private fun getTimer(defaultMessageTimeOut: Long): CountDownTimer {
        return object : CountDownTimer(defaultMessageTimeOut, defaultMessageTimeOut) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                if (!onMessageReadyCalled) {
                    onMessageReady = null
                    webView!!.onErrorOccurred(ConsentLibException("a timeout has occurred when loading the message"))
                }
            }
        }
    }

    private fun setSharedPreference(key: String, value: String) {
        val editor = sharedPref.edit()
        editor.putString(key, value)
        editor.apply()
    }


    private fun setSharedPreference(key: String, value: Boolean?) {
        val editor = sharedPref.edit()
        editor.putBoolean(key, value!!)
        editor.apply()
    }

    private fun setSubjectToGDPR() {

        sourcePoint.getGDPRStatus(object : OnLoadComplete {
            override fun onSuccess(gdprApplies: Any) {
                setSharedPreference(IAB_CONSENT_SUBJECT_TO_GDPR, if (gdprApplies == "true") "1" else "0")
            }

            override fun onFailure(exception: ConsentLibException) {
                Log.d(TAG, "Failed setting the preference IAB_CONSENT_SUBJECT_TO_GDPR")
            }
        })
    }

    private fun setIABVars(euconsent: String) {
        setSharedPreference(IAB_CONSENT_CONSENT_STRING, euconsent)

        val vendorConsent = VendorConsentDecoder.fromBase64String(euconsent)

        // Construct and save parsed purposes string
        val allowedPurposes = CharArray(MAX_PURPOSE_ID)
        for (i in 0 until MAX_PURPOSE_ID) {
            allowedPurposes[i] = if (vendorConsent.isPurposeAllowed(i + 1)) '1' else '0'
        }
        Log.i(TAG, "allowedPurposes: " + String(allowedPurposes))
        setSharedPreference(IAB_CONSENT_PARSED_PURPOSE_CONSENTS, String(allowedPurposes))

        // Construct and save parsed vendors string
        val allowedVendors = CharArray(vendorConsent.getMaxVendorId())
        for (i in allowedVendors.indices) {
            allowedVendors[i] = if (vendorConsent.isVendorAllowed(i + 1)) '1' else '0'
        }
        Log.i(TAG, "allowedVendors: " + String(allowedVendors))
        setSharedPreference(IAB_CONSENT_PARSED_VENDOR_CONSENTS, String(allowedVendors))
    }

    /**
     * This method receives an Array of Strings representing the custom vendor ids you want to get
     * the consents for and a callback.<br></br>
     * The callback will be called with an Array of booleans once the data is ready. If the element
     * *i* of this array is *true* it means the user has consented to the vendor index *i*
     * from the customVendorIds parameter. Otherwise it will be *false*.
     *
     * @param callback        - callback that will be called with an array of boolean indicating if the user has given consent or not to those vendors.
     */
    fun getCustomVendorConsents(callback: OnLoadComplete) {
        loadAndStoreCustomVendorAndPurposeConsents(arrayOfNulls<String>(0), object : OnLoadComplete {
            override fun onSuccess(result: Any) {
                val consents = result as HashSet<Consent>
                val vendorConsents = HashSet<CustomVendorConsent>()
                for (consent in consents) {
                    if (consent is CustomVendorConsent) {
                        vendorConsents.add(consent)
                    }
                }
                callback.onSuccess(vendorConsents)
            }

            override fun onFailure(exception: ConsentLibException) {
                Log.d(TAG, "Failed getting custom vendor consents.")
                callback.onFailure(exception)
            }
        })
    }

    /**
     * This method receives a callback which is called with an Array of all the purposes ([Consent]) the user has given consent for.
     *
     * @param callback called with an array of [Consent]
     */
    fun getCustomPurposeConsents(callback: OnLoadComplete) {
        loadAndStoreCustomVendorAndPurposeConsents(arrayOfNulls<String>(0), object : OnLoadComplete {
            override fun onSuccess(result: Any) {
                val consents = result as HashSet<Consent>
                val purposeConsents = HashSet<CustomPurposeConsent>()
                for (consent in consents) {
                    if (consent is CustomPurposeConsent) {
                        purposeConsents.add(consent)
                    }
                }
                callback.onSuccess(purposeConsents)
            }

            override fun onFailure(exception: ConsentLibException) {
                Log.d(TAG, "Failed getting custom purpose consents.")
                callback.onFailure(exception)
            }
        })
    }

    private fun loadAndStoreCustomVendorAndPurposeConsents(vendorIds: Array<String?>, callback: OnLoadComplete) {
        val siteIdKey = SP_SITE_ID + "_" + accountId + "_" + siteName
        val siteID = Integer.toString(siteId!!)
        val editor = sharedPref.edit()
        editor.putString(siteIdKey, siteID)
        editor.apply()
        sourcePoint.getCustomConsents(consentUUID!!, euconsent!!, siteID, vendorIds, object : OnLoadComplete {
            override fun onSuccess(result: Any) {
                val consents = result as HashSet<Consent>
                val consentStrings = HashSet<String>()
                val editor = sharedPref.edit()
                clearStoredVendorConsents(vendorIds, editor)
                for (consent in consents) {
                    consentStrings.add(consent.toJSON().toString())
                }
                editor.putStringSet(CUSTOM_CONSENTS_KEY, consentStrings)
                editor.apply()
                callback.onSuccess(consents)
            }

            override fun onFailure(exception: ConsentLibException) {
                callback.onFailure(exception)
            }
        })
    }

    /**
     * Given a list of IAB vendor IDs, returns a corresponding array of boolean each representing
     * the consent was given or not to the requested vendor.
     *
     * @param vendorIds an array of standard IAB vendor IDs.
     * @return an array with same size as vendorIds param representing the results in the same order.
     * @throws ConsentLibException if the consent is not dialog completed or the
     * consent string is not present in SharedPreferences.
     */
    @Throws(ConsentLibException::class)
    fun getIABVendorConsents(vendorIds: IntArray): BooleanArray {
        val vendorConsent = getParsedConsentString()
        val results = BooleanArray(vendorIds.size)

        for (i in vendorIds.indices) {
            results[i] = vendorConsent.isVendorAllowed(vendorIds[i])
        }
        return results
    }

    /**
     * Given a list of IAB Purpose IDs, returns a corresponding array of boolean each representing
     * the consent was given or not to the requested purpose.
     *
     * @param purposeIds an array of standard IAB purpose IDs.
     * @return an array with same size as purposeIds param representing the results in the same order.
     * @throws ConsentLibException if the consent dialog is not completed or the
     * consent string is not present in SharedPreferences.
     */
    @Throws(ConsentLibException::class)
    fun getIABPurposeConsents(purposeIds: IntArray): BooleanArray {
        val vendorConsent = getParsedConsentString()
        val results = BooleanArray(purposeIds.size)

        for (i in purposeIds.indices) {
            results[i] = vendorConsent.isPurposeAllowed(purposeIds[i])
        }
        return results
    }

    @Throws(ConsentLibException::class)
    private fun getConsentStringFromPreferences(): String {
        return sharedPref.getString(IAB_CONSENT_CONSENT_STRING, null)
            ?: throw ConsentLibException("Could not find consent string in sharedUserPreferences.")
    }

    @Throws(ConsentLibException::class)
    private fun getParsedConsentString(): VendorConsent {
        val euconsent = getConsentStringFromPreferences()
        val parsedConsent: VendorConsent
        try {
            parsedConsent = VendorConsentDecoder.fromBase64String(euconsent)
        } catch (e: Exception) {
            throw ConsentLibException("Unable to parse raw string \"$euconsent\" into consent string.")
        }

        return parsedConsent
    }

    /**
     * When we receive data from the server, if a given custom vendor is no longer given consent
     * to, its information won't be present in the payload. Therefore we have to first clear the
     * preferences then set each vendor to true based on the response.
     */
    private fun clearStoredVendorConsents(customVendorIds: Array<String?>, editor: SharedPreferences.Editor) {
        for (vendorId in customVendorIds) {
            editor.remove(CUSTOM_CONSENTS_KEY + vendorId)
        }
    }

    /*call this method to clear sharedPreferences from app onError*/
    fun clearAllConsentData() {
        val editor = sharedPref.edit()
        editor.remove(IAB_CONSENT_CMP_PRESENT)
        editor.remove(IAB_CONSENT_CONSENT_STRING)
        editor.remove(IAB_CONSENT_PARSED_PURPOSE_CONSENTS)
        editor.remove(IAB_CONSENT_PARSED_VENDOR_CONSENTS)
        editor.remove(IAB_CONSENT_SUBJECT_TO_GDPR)
        editor.remove(CONSENT_UUID_KEY)
        editor.remove(EU_CONSENT_KEY)
        editor.remove(CUSTOM_CONSENTS_KEY)
        editor.commit()
    }

    private fun runOnLiveActivityUIThread(uiRunnable: () -> Unit) {
        if (activity != null && !activity!!.isFinishing()) {
            activity!!.runOnUiThread(uiRunnable)
        }
    }

    private fun displayWebViewIfNeeded() {
        if (weOwnTheView) {
            runOnLiveActivityUIThread {
                if (webView != null) {
                    if (webView!!.getParent() != null) {
                        (webView!!.getParent() as ViewGroup).removeView(webView) // <- fix
                    }
                    webView!!.display()
                    viewGroup!!.addView(webView)
                }
            }
        }
    }

    private fun removeWebViewIfNeeded() {
        if (weOwnTheView && activity != null) destroy()
    }

    private fun finish() {
        runOnLiveActivityUIThread {
            removeWebViewIfNeeded()
            this.onConsentReady!!.run(this@ConsentLib)
            activity = null // release reference to activity
        }
    }

    fun destroy() {
        if (mCountDownTimer != null) mCountDownTimer!!.cancel()
        if (webView != null) {
            if (viewGroup != null) {
                viewGroup!!.removeView(webView)
            }
            webView!!.destroy()
            webView = null
        }
    }
    companion object {
    @JvmStatic
        fun newBuilder(
            accountId: Int,
            siteName: String,
            siteId: Int,
            pmId: String,
            activity: Activity
        ): ConsentLibBuilder {
            return ConsentLibBuilder(accountId, siteName, siteId, pmId, activity)
        }
    }
}