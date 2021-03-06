package com.sourcepoint.gdpr_cmplibrary;

public class ConsentLibException extends Exception {

    public String consentLibErrorMessage;

    ConsentLibException(Throwable e, String  message){ super(e); consentLibErrorMessage = message;}
    ConsentLibException(String  message){ super(); consentLibErrorMessage = message;}

    public static class BuildException extends ConsentLibException {
        public static final String description = "Error during ConsentLib build";

        BuildException(Throwable e, String message) { super(e, description + ": " + message); }
        BuildException(String message) { super(description + ": " + message); }
    }

    public static class NoInternetConnectionException extends ConsentLibException {
        public static final String description = "Error due to no internet connection";

        NoInternetConnectionException(Throwable e, String message) { super(e, description + ": " + message); }
        NoInternetConnectionException(String message) { super(description + ": " + message); }

    }

    public static class ApiException extends ConsentLibException {
        public static final String description = "Error due to android API";
        ApiException(Throwable e, String message) { super(e, description + ": " + message); }
        ApiException(String message) { super(description + ": " + message); }
    }
}
