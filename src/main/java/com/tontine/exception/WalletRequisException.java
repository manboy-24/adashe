package com.tontine.exception;

public class WalletRequisException extends RuntimeException {
    public WalletRequisException() {
        super("Vous devez enregistrer au moins un numéro MTN MoMo ou Orange Money avant de rejoindre une tontine.");
    }
}
