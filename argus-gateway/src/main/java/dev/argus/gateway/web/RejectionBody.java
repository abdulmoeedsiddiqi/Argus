package dev.argus.gateway.web;

/**
 * JSON body of every rejection (QR-18): a machine-readable reason code and a short message.
 * Never contains stack traces or internal details.
 */
record RejectionBody(String reason, String message) { }
