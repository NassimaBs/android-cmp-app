package com.sourcepointmeta.app.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.ViewModel;
import android.arch.lifecycle.ViewModelProvider;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.TextInputEditText;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SwitchCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import com.sourcepoint.gdpr_cmplibrary.ConsentLibBuilder;
import com.sourcepoint.gdpr_cmplibrary.GDPRConsentLib;
import com.sourcepoint.gdpr_cmplibrary.GDPRUserConsent;
import com.sourcepointmeta.app.R;
import com.sourcepointmeta.app.SourcepointApp;
import com.sourcepointmeta.app.adapters.TargetingParamsAdapter;
import com.sourcepointmeta.app.common.Constants;
import com.sourcepointmeta.app.database.entity.Property;
import com.sourcepointmeta.app.database.entity.TargetingParam;
import com.sourcepointmeta.app.listeners.RecyclerViewClickListener;
import com.sourcepointmeta.app.models.Consents;
import com.sourcepointmeta.app.repository.PropertyListRepository;
import com.sourcepointmeta.app.utility.Util;
import com.sourcepointmeta.app.viewmodel.NewPropertyViewModel;
import com.sourcepointmeta.app.viewmodel.ViewModelUtils;

import java.util.ArrayList;
import java.util.List;

import static android.support.v7.widget.RecyclerView.VERTICAL;

public class NewPropertyActivity extends BaseActivity<NewPropertyViewModel> {

    private final String TAG = "NewPropertyActivity";
    private ProgressDialog mProgressDialog;
    private TextInputEditText mAccountIdET, mPropertyIdET, mPropertyNameET, mPMIdET, mAuthIdET, mKeyET, mValueET;

    private TextView mAddParamBtn;
    private ViewGroup mMainViewGroup;

    private SwitchCompat mStagingSwitch;
    private TextView mTitle;
    private AlertDialog mAlertDialog;
    private TargetingParamsAdapter mTargetingParamsAdapter;
    private List<TargetingParam> mTargetingParamList = new ArrayList<>();
    private TextView mAddParamMessage;
    private boolean messageVisible = false;
    private boolean onConsentReadyCalled = false;
    private boolean isShow = false;
    private List<Consents> mVendorConsents = new ArrayList<>();
    private List<Consents> mPurposeConsents = new ArrayList<>();
    private ArrayList<Consents> mConsentList = new ArrayList<>();
    private GDPRConsentLib mGDPRConsentLib;

    private void showMessageWebView(View view) {
        view.setLayoutParams(new ViewGroup.LayoutParams(0, 0));
        view.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
        view.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;
        view.bringToFront();
        view.requestLayout();
        messageVisible = true;
        invalidateOptionsMenu();
        if (view != null && view.getParent() != null) {
            mMainViewGroup.removeView(view);
        }
        mMainViewGroup.addView(view);
    }

    private void removeWebView(View view) {
        if (view != null && view.getParent() != null) {
            messageVisible = false;
            invalidateOptionsMenu();
            mMainViewGroup.removeView(view);
        }
    }

    private GDPRConsentLib buildConsentLib(Property property, Activity activity) {
        ConsentLibBuilder consentLibBuilder = GDPRConsentLib.newBuilder(property.getAccountID(), property.getProperty(), property.getPropertyID(), property.getPmID(), activity)
                .setStagingCampaign(property.isStaging())
                .setMessageTimeOut(15000)
                .setOnConsentUIReady(view -> {
                            getSupportActionBar().hide();
                            hideProgressBar();
                            Log.i(TAG, "The message is about to be shown.");
                            isShow = true;
                            showMessageWebView(view);
                        }
                ).setOnConsentUIFinished(view -> {
                    getSupportActionBar().show();
                    Log.i(TAG, "The message is removed.");
                    removeWebView(view);

                })
                .setOnConsentReady(userConsent -> {
                    runOnUiThread(this::showProgressBar);
                    onConsentReadyCalled = true;
                    saveToDatabase(property);
                    getConsentsFromConsentLib(userConsent);
                    Log.d(TAG, "OnConsentReady");
                    runOnUiThread(() -> {
                        if (!isShow && onConsentReadyCalled) {
                            showAlertDialogForMessageShowOnce(getResources().getString(R.string.no_message_matching_scenario), property);
                        } else {
                            startConsentViewActivity(property);
                        }
                    });
                })
                .setOnError(error -> {
                    hideProgressBar();
                    Log.d(TAG, "setOnError");
                    showAlertDialog("" + error.consentLibErrorMessage);
                    Log.d(TAG, "Something went wrong: ", error);
                });

        //get and set targeting param
        List<TargetingParam> list = property.getTargetingParamList();//getTargetingParamList(property);
        for (TargetingParam tps : list) {
            consentLibBuilder.setTargetingParam(tps.getKey(), tps.getValue());
        }

        if (!TextUtils.isEmpty(property.getAuthId())) {
            consentLibBuilder.setAuthId(property.getAuthId());
        } else {
            Log.d(TAG, "AuthID Not available : " + property.getAuthId());
        }
        // generate ConsentLib at this point modifying builder will not do anything
        return consentLibBuilder.build();
    }

    public void getConsentsFromConsentLib(GDPRUserConsent userConsent) {

        ArrayList<String> acceptedVendors = userConsent.acceptedVendors;
        ArrayList<String> acceptedPurposes = userConsent.acceptedCategories;

        if (acceptedVendors.size() > 0) {
            Consents vendorHeader = new Consents("0", "Accepted Vendor Consents Ids", "Header");
            mVendorConsents.add(vendorHeader);
            for (String vendorId : acceptedVendors) {
                Log.i(TAG, "The vendor " + vendorId + " was accepted.");
                Consents vendorConsent = new Consents(vendorId, vendorId, "vendorConsents");
                mVendorConsents.add(vendorConsent);
            }
        }
        if (acceptedPurposes.size() > 0) {
            Consents purposeHeader = new Consents("0", "Accepted Purpose Consents Ids", "Header");
            mPurposeConsents.add(purposeHeader);
            for (String purposeId : acceptedPurposes) {
                Log.i(TAG, "The category " + purposeId + " was accepted.");
                Consents purposeConsents = new Consents(purposeId, purposeId, "purposeConsents");
                mPurposeConsents.add(purposeConsents);
            }
        }

        mConsentList.addAll(mVendorConsents);
        mConsentList.addAll(mPurposeConsents);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_property);

        getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
        getSupportActionBar().setCustomView(R.layout.tool_bar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        mTitle = getSupportActionBar().getCustomView().findViewById(R.id.toolbar_title);
        mMainViewGroup = findViewById(android.R.id.content);
        setupUI();
    }

    private void setupUI() {
        mAccountIdET = findViewById(R.id.etAccountID);
        mPropertyIdET = findViewById(R.id.etPropertyId);
        mPropertyNameET = findViewById(R.id.etPropertyName);
        mPMIdET = findViewById(R.id.etPMId);
        mAuthIdET = findViewById(R.id.etAuthID);
        mStagingSwitch = findViewById(R.id.toggleStaging);
        mStagingSwitch.setChecked(false);

        mKeyET = findViewById(R.id.etKey);
        mValueET = findViewById(R.id.etValue);
        mAddParamBtn = findViewById(R.id.btn_addParams);
        mAddParamBtn.setOnClickListener(v -> addTargetingParam());

        RecyclerView tpRecyclerView = findViewById(R.id.targetingParamsRecycleView);
        DividerItemDecoration itemDecor = new DividerItemDecoration(this, VERTICAL);
        tpRecyclerView.addItemDecoration(itemDecor);
        tpRecyclerView.setNestedScrollingEnabled(false);

        RecyclerViewClickListener listener = getRecyclerViewClickListener();
        mTargetingParamsAdapter = new TargetingParamsAdapter(listener);
        mTargetingParamsAdapter.setmTargetingParamsList(mTargetingParamList);
        tpRecyclerView.setAdapter(mTargetingParamsAdapter);
        mTargetingParamsAdapter.notifyDataSetChanged();

        mAddParamMessage = findViewById(R.id.tv_noTargetingParams);
        setAddParamsMessage();

        Bundle data = getIntent().getExtras();
        if (data != null) {
            Property property = data.getParcelable(Constants.PROPERTY);

            if (property != null) {
                mAccountIdET.setText(String.valueOf(property.getAccountID()));
                mPropertyIdET.setText(String.valueOf(property.getPropertyID()));
                mPropertyNameET.setText(property.getProperty());
                mPMIdET.setText(property.getPmID());
                mStagingSwitch.setChecked(property.isStaging());

                if (!TextUtils.isEmpty(property.getAuthId())) {
                    mAuthIdET.setText(property.getAuthId());
                }
                mTargetingParamList = property.getTargetingParamList();
                mTargetingParamsAdapter.setmTargetingParamsList(mTargetingParamList);
                if (mTargetingParamList.size() != 0)
                    mAddParamMessage.setVisibility(View.GONE);
                mTargetingParamsAdapter.notifyDataSetChanged();

                mTitle.setText(R.string.edit_property_title);
            }
        } else {
            mTitle.setText(R.string.new_property_title);
        }

        mValueET.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addTargetingParam();
                hideSoftKeyboard();
                return true;
            }
            return false;
        });
        // hides keyboard when touch outside
        mAccountIdET.setOnFocusChangeListener(this::hideSoftKeyboard);
        mPropertyIdET.setOnFocusChangeListener(this::hideSoftKeyboard);
        mPropertyNameET.setOnFocusChangeListener(this::hideSoftKeyboard);
        mPMIdET.setOnFocusChangeListener(this::hideSoftKeyboard);
        mAuthIdET.setOnFocusChangeListener(this::hideSoftKeyboard);
        mKeyET.setOnFocusChangeListener(this::hideSoftKeyboard);
        mValueET.setOnFocusChangeListener(this::hideSoftKeyboard);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ViewModelProvider.Factory viewModelFactory = ViewModelUtils.createFor(viewModel);
        viewModel = ViewModelProviders.of(this, viewModelFactory).get(viewModel.getClass());
    }

    @Override
    ViewModel getViewModel() {
        PropertyListRepository propertyListRepository = ((SourcepointApp) getApplication()).getPropertyListRepository();
        return new NewPropertyViewModel(propertyListRepository);
    }

    private void showProgressBar() {

        if (mProgressDialog == null) {

            mProgressDialog = new ProgressDialog(NewPropertyActivity.this);
            mProgressDialog.setMessage("Please wait...");
            mProgressDialog.setCancelable(false);
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.show();

        } else if (!mProgressDialog.isShowing()) {
            mProgressDialog.show();
        }
    }

    private void hideProgressBar() {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (mProgressDialog != null) {
            hideProgressBar();
            mProgressDialog = null;
        }
    }

    private void showAlertDialog(String message) {
        if (!(mAlertDialog != null && mAlertDialog.isShowing())) {
            AlertDialog.Builder alertDialog = new AlertDialog.Builder(NewPropertyActivity.this)
                    .setMessage(message)
                    .setCancelable(false)
                    .setPositiveButton("OK", (dialog, which) -> dialog.cancel()
                    );
            mAlertDialog = alertDialog.create();
        }
        mAlertDialog.show();
    }

    private void showAlertDialogForMessageShowOnce(String message, Property property) {
        if (!(mAlertDialog != null && mAlertDialog.isShowing())) {
            AlertDialog.Builder alertDialog = new AlertDialog.Builder(NewPropertyActivity.this)
                    .setMessage(message)
                    .setCancelable(false)
                    .setPositiveButton("OK", (dialog, which) -> {
                                dialog.cancel();
                                startConsentViewActivity(property);
                            }
                    );
            mAlertDialog = alertDialog.create();
        }
        mAlertDialog.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.new_property, menu);
        menu.findItem(R.id.action_saveProperty).setEnabled(!messageVisible);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        hideSoftKeyboard();

        switch (item.getItemId()) {
            case R.id.action_saveProperty:
                loadPropertyWithInput();
                break;
            case android.R.id.home:
                this.finish();
                onBackPressed();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    // validate user data input
    private Property getFormData() {

        String accountID = mAccountIdET.getText().toString().trim();
        String PropertyID = mPropertyIdET.getText().toString().trim();
        String propertyName = mPropertyNameET.getText().toString().trim();
        String pmID = mPMIdET.getText().toString().trim();
        String authId = mAuthIdET.getText().toString().trim();
        boolean isStaging = mStagingSwitch.isChecked();
        boolean isShowPm = false;
        if (TextUtils.isEmpty(accountID)) {
            return null;
        }
        if (TextUtils.isEmpty(propertyName)) {
            return null;
        }
        if (TextUtils.isEmpty(PropertyID)) {
            return null;
        }
        if (TextUtils.isEmpty(pmID)) {
            return null;
        }
        int account = Integer.parseInt(accountID);
        int property_id = Integer.parseInt(PropertyID);

        return new Property(account, property_id, propertyName, pmID, isStaging, isShowPm, authId, mTargetingParamList);
    }

    private void loadPropertyWithInput() {

        Property property = getFormData();
        if (property == null) {
            showAlertDialog(getString(R.string.empty_accountid_propertyname_message));
        } else {
            showProgressBar();
            LiveData<Integer> listSize = viewModel.getPropertyWithDetails(property);
            listSize.observe(this, size -> {
                if (size > 0) {
                    showAlertDialog(getResources().getString(R.string.property_details_exists));
                    hideProgressBar();
                } else {
                    mGDPRConsentLib = buildConsentLib(property, this);
                    if (Util.isNetworkAvailable(this)) {
                        showProgressBar();
                        mGDPRConsentLib.run();
                    } else {
                        showAlertDialog(getString(R.string.network_check_message));
                        hideProgressBar();
                    }
                }
                listSize.removeObservers(this);
            });
        }
    }

    private TargetingParam getTargetingParam() {
        String key = mKeyET.getText().toString().trim();
        String value = mValueET.getText().toString().trim();
        if (TextUtils.isEmpty(key)) {
            return null;
        }
        if (TextUtils.isEmpty(value)) {
            return null;
        }
        mKeyET.setText("");
        mValueET.setText("");
        mKeyET.clearFocus();
        mValueET.clearFocus();
        return new TargetingParam(key, value);
    }

    private void addTargetingParam() {
        TargetingParam targetingParam = getTargetingParam();
        if (targetingParam == null) {
            showAlertDialog("Please enter targeting param Key/Value");
        } else if (mTargetingParamList.contains(targetingParam)) {
            for (TargetingParam param : mTargetingParamList) {
                if (param.getKey().equals(targetingParam.getKey()))
                    param.setValue(targetingParam.getValue());
            }
        } else {
            mTargetingParamList.add(targetingParam);
        }

        mTargetingParamsAdapter.setmTargetingParamsList(mTargetingParamList);
        mTargetingParamsAdapter.notifyDataSetChanged();
        setAddParamsMessage();
    }

    //hides soft keyboard
    private void hideSoftKeyboard() {
        if (this.getCurrentFocus() != null) {
            InputMethodManager inputMethodManager = (InputMethodManager) this
                    .getSystemService(Activity.INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(this.getCurrentFocus().getWindowToken(),
                    0);
        }
        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
    }

    private void hideSoftKeyboard(View v, boolean hasFocus) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (!hasFocus) {
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        } else {
            imm.showSoftInput(v, 0);
        }
    }

    private void startConsentViewActivity(Property property) {

        Intent intent = new Intent(NewPropertyActivity.this, ConsentViewActivity.class);
        intent.putExtra(Constants.PROPERTY, property);
        intent.putParcelableArrayListExtra(Constants.CONSENTS, mConsentList);
        startActivity(intent);
    }

    private RecyclerViewClickListener getRecyclerViewClickListener() {
        return (view, position) -> {
            mTargetingParamList.remove(mTargetingParamList.get(position));
            mTargetingParamsAdapter.notifyDataSetChanged();
            setAddParamsMessage();
        };
    }

    private void setAddParamsMessage() {
        if (mTargetingParamList != null && mTargetingParamList.size() == 0) {
            mAddParamMessage.setVisibility(View.VISIBLE);
        } else {
            mAddParamMessage.setVisibility(View.GONE);
        }
    }

    // method to update or add property to database
    private void saveToDatabase(Property property) {
        Bundle bundle = getIntent().getExtras();
        Log.d(TAG, "saveToDatabase");
        if (bundle != null && bundle.containsKey("Update")) {
            if (property != null && bundle.getString("Update") != null)
                property.setId(Integer.parseInt(bundle.getString("Update")));
            updateProperty(property);
        } else {
            addProperty(property);
        }
    }

    private void addProperty(Property property) {
        viewModel.addProperty(property);
    }

    private void updateProperty(Property property) {
        viewModel.updateProperty(property);
    }
}
