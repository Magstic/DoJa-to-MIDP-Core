package com.nttdocomo.io;

/** 對應 DoJa 連線層的執行期例外型別。 */
public class ConnectionException extends RuntimeException {
    public ConnectionException() { super(); }
    public ConnectionException(String message) { super(message); }
}
