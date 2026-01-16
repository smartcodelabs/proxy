package me.internalizable.numdrassl.auth;

import io.netty.incubator.codec.quic.QuicChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.net.ssl.SSLEngine;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Base64;

/**
 * Utilities for extracting and working with client certificates.
 */
public class CertificateExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(CertificateExtractor.class);

    /**
     * Extract the client's X509 certificate from a QUIC channel.
     * This certificate is what the client presented during the mTLS handshake.
     */
    @Nullable
    public static X509Certificate extractClientCertificate(@Nonnull QuicChannel channel) {
        try {
            SSLEngine sslEngine = channel.sslEngine();
            if (sslEngine == null) {
                LOGGER.debug("No SSL engine available on channel");
                return null;
            }

            Certificate[] peerCerts = sslEngine.getSession().getPeerCertificates();
            if (peerCerts != null && peerCerts.length > 0 && peerCerts[0] instanceof X509Certificate) {
                X509Certificate cert = (X509Certificate) peerCerts[0];
                LOGGER.debug("Extracted client certificate: {}", cert.getSubjectX500Principal().getName());
                return cert;
            }

            LOGGER.debug("No peer certificates available");
            return null;
        } catch (Exception e) {
            LOGGER.debug("Failed to extract client certificate: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Compute the SHA-256 fingerprint of a certificate in URL-safe Base64 format.
     * This matches the format Hytale uses for certificate binding.
     */
    @Nullable
    public static String computeCertificateFingerprint(@Nonnull X509Certificate certificate) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(certificate.getEncoded());
            // URL-safe Base64 without padding (matches Hytale's format)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            LOGGER.error("Failed to compute certificate fingerprint", e);
            return null;
        }
    }

    /**
     * Extract and compute the fingerprint in one step.
     */
    @Nullable
    public static String extractClientCertificateFingerprint(@Nonnull QuicChannel channel) {
        X509Certificate cert = extractClientCertificate(channel);
        if (cert == null) {
            return null;
        }
        return computeCertificateFingerprint(cert);
    }
}

