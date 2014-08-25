package com.lr.androidemailpgp.crypto;


@SuppressWarnings("serial")
public class VerificationFailedException extends Exception {
    public VerificationFailedException(String message) {
        super(message);
    }
}
