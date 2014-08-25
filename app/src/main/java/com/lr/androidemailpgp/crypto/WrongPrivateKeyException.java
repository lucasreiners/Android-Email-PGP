package com.lr.androidemailpgp.crypto;


@SuppressWarnings("serial")
public class WrongPrivateKeyException extends Exception {
    public WrongPrivateKeyException(String message) {
        super(message);
    }
}
