package com.sourcepoint.cmplibrary;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import org.json.JSONException;
import org.json.JSONObject;

public class ConsentLibBuilder {
    private final ConsentLib.Callback noOpCallback = new ConsentLib.Callback() {
        @Override public void run(ConsentLib c) { }
    };
    private final JSONObject targetingParams = new JSONObject();

    Activity activity;
    int accountId;
    String siteName;
    String mmsDomain, cmpDomain, msgDomain = null;
    String page = "";
    ViewGroup viewGroup = null;
    ConsentLib.Callback onMessageChoiceSelect, onInteractionComplete = noOpCallback;
    boolean staging, stagingCampaign = false;
    EncodedParam targetingParamsString = null;
    ConsentLib.DebugLevel debugLevel = ConsentLib.DebugLevel.OFF;

    ConsentLibBuilder(Integer accountId, String siteName, Activity activity) {
        this.accountId = accountId;
        this.siteName = siteName;
        this.activity = activity;
    }

    /**
     *  <b>Optional</b> Sets the page name in which the WebView was shown. Used for logging only.
     * @param p - a string representing page, e.g "/home"
     * @return ConsentLibBuilder - the next build step
     * @see ConsentLibBuilder
     */
    public ConsentLibBuilder setPage(String p) {
        page = p;
        return this;
    }

    /**
     *  <b>Optional</b> Sets the view group in which WebView will will be rendered into.
     *  If it's not called or called with null, the MainView will be used instead.
     *  In case the main view is not a ViewGroup, a BuildException will be thrown during
     *  when build() is called.
     * @param v - the view group
     * @return ConsentLibBuilder - the next build step
     * @see ConsentLibBuilder
     */
    public ConsentLibBuilder setViewGroup(ViewGroup v) {
        viewGroup = v;
        return this;
    }

    // TODO: add what are the possible choices returned to the Callback
    /**
     *  <b>Optional</b> Sets the Callback to be called when the user selects an option on the WebView.
     *  The selected choice will be available in the instance variable ConsentLib.choiceType
     * @param c - a callback that will be called when the user selects an option on the WebView
     * @return ConsentLibBuilder - the next build step
     * @see ConsentLibBuilder
     */
    public ConsentLibBuilder setOnMessageChoiceSelect(ConsentLib.Callback c) {
        onMessageChoiceSelect = c;
        return this;
    }

    /**
     *  <b>Optional</b> Sets the Callback to be called when the user finishes interacting with the WebView
     *  either by closing it, canceling or accepting the terms.
     *  At this point, the following keys will available populated in the sharedStorage:
     *  <ul>
     *      <li>{@link ConsentLib#EU_CONSENT_KEY}</li>
     *      <li>{@link ConsentLib#CONSENT_UUID_KEY}</li>
     *      <li>{@link ConsentLib#IAB_CONSENT_SUBJECT_TO_GDPR}</li>
     *      <li>{@link ConsentLib#IAB_CONSENT_CONSENT_STRING}</li>
     *      <li>{@link ConsentLib#IAB_CONSENT_PARSED_PURPOSE_CONSENTS}</li>
     *      <li>{@link ConsentLib#IAB_CONSENT_PARSED_VENDOR_CONSENTS}</li>
     *  </ul>
     *  Also at this point, the methods {@link ConsentLib#getCustomVendorConsents},
     *  {@link ConsentLib#getCustomPurposeConsents}
     *  will also be able to be called from inside the callback.
     * @param c - Callback to be called when the user finishes interacting with the WebView
     * @return ConsentLibBuilder - the next build step
     * @see ConsentLibBuilder
     */
    public ConsentLibBuilder setOnInteractionComplete(ConsentLib.Callback c) {
        onInteractionComplete = c;
        return this;
    }

    /**
     * <b>Optional</b> True for <i>staging</i> campaigns or False for <i>production</i>
     * campaigns. <b>Default:</b> false
     * @param st - True for <i>staging</i> campaigns or False for <i>production</i>
     * @return ConsentLibBuilder - the next build step
     * @see ConsentLibBuilder
     */
    public ConsentLibBuilder setStage(boolean st) {
        stagingCampaign = st;
        return this;
    }

    /**
     * <b>Optional</b> This parameter refers to SourcePoint's environment itself. True for staging
     * or false for production. <b>Default:</b> false
     * @param st - True for staging or false for production
     * @return ConsentLibBuilder - the next build step
     * @see ConsentLibBuilder
     */
    public ConsentLibBuilder setInternalStage(boolean st) {
        staging = st;
        return this;
    }

    public ConsentLibBuilder setInAppMessagePageUrl(String inAppMessageUrl) {
        msgDomain = inAppMessageUrl;
        return this;
    }

    public ConsentLibBuilder setMmsDomain(String mmsDomain) {
        this.mmsDomain = mmsDomain;
        return this;
    }

    public ConsentLibBuilder setCmpDomain(String cmpDomain) {
        this.cmpDomain = cmpDomain;
        return this;
    }

    // TODO: document these.
    public ConsentLibBuilder setTargetingParam(String key, Integer val)
            throws ConsentLibException.BuildException  {
        return setTargetingParam(key, (Object) val);
    }

    public ConsentLibBuilder setTargetingParam(String key, String val)
            throws ConsentLibException.BuildException {
        return setTargetingParam(key, (Object) val);
    }

    private ConsentLibBuilder setTargetingParam(String key, Object val) throws ConsentLibException.BuildException {
        try {
            this.targetingParams.put(key, val);
        } catch (JSONException e) {
            throw new ConsentLibException
                    .BuildException("error parsing targeting param, key: "+key+" value: "+val);
        }
        return this;
    }

    /**
     * <b>Optional</b> Sets the DEBUG level.
     * <i>(Not implemented yet)</i>
     * <b>Default</b>{@link ConsentLib.DebugLevel#DEBUG}
     * @param l - one of the values of {@link ConsentLib.DebugLevel#DEBUG}
     * @return ConsentLibBuilder - the next build step
     * @see ConsentLibBuilder
     */
    public ConsentLibBuilder setDebugLevel(ConsentLib.DebugLevel l) {
        debugLevel = l;
        return this;
    }

    private void setTargetingParamsString() throws ConsentLibException {
        targetingParamsString = new EncodedParam("targetingParams", targetingParams.toString());
    }

    private void setDefaults () throws ConsentLibException.BuildException {
        if (viewGroup == null) {
            // render on top level activity view if no viewGroup specified
            View view = activity.getWindow().getDecorView().findViewById(android.R.id.content);
            if (view instanceof ViewGroup) {
                viewGroup = (ViewGroup) view;
            } else {
                throw new ConsentLibException.BuildException("Current window is not a ViewGroup, can't render WebView");
            }
        }
    }

    /**
     * Run internal tasks and build the ConsentLib. This method will validate the
     * data coming from the previous Builders and throw {@link ConsentLibException.BuildException}
     * in case something goes wrong.
     * @return ConsentLib
     * @throws ConsentLibException.BuildException - if any of the required data is missing or invalid
     */
    public ConsentLib build() throws ConsentLibException {
        try {
            setDefaults();
            setTargetingParamsString();
        } catch (ConsentLibException e) {
            this.activity = null; // release reference to activity
            throw new ConsentLibException.BuildException(e.getMessage());
        }

        return new ConsentLib(this);
    }
}