package com.sourcepoint.gdpr_cmplibrary;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(RobolectricTestRunner.class)
public class ConsentWebViewTest {

    private ConsentWebView spyView;

    @Before
    public void setUp() {

        ConsentWebView consentWebView = new ConsentWebView(RuntimeEnvironment.systemContext) {
            @Override
            public void onConsentUIReady() {   }

            @Override
            public void onError(ConsentLibException error) {  }

            @Override
            public void onAction(ConsentAction action) {  }

            @Override
            public void onBackPressAction() {

            }
        };
        spyView = spy(consentWebView);
    }

    @Test
    public void onConsentUIReady(){
        spyView.onConsentUIReady();
        verify(spyView,times(1)).onConsentUIReady();
    }

    @Test
    public void onError(){
        ConsentLibException consentLibException = new ConsentLibException("something bad just happened");
        spyView.onError(consentLibException);
        verify(spyView, times(1)).onError(consentLibException);
    }
}


