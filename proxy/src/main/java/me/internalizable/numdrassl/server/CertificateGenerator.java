package me.internalizable.numdrassl.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.util.Base64;

import javax.annotation.Nonnull;

/**
 * Generates self-signed SSL certificates for the QUIC proxy server.
 * Uses keytool command-line utility for certificate generation.
 */
public class CertificateGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(CertificateGenerator.class);

    /**
     * Generate a self-signed certificate and private key using keytool
     */
    public static void generateSelfSigned(@Nonnull String certPath, @Nonnull String keyPath) throws Exception {
        LOGGER.info("Generating self-signed SSL certificate...");

        // Create directories if needed
        Path certDir = Paths.get(certPath).getParent();
        Path keyDir = Paths.get(keyPath).getParent();
        if (certDir != null) Files.createDirectories(certDir);
        if (keyDir != null) Files.createDirectories(keyDir);

        // Generate RSA key pair
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048, new SecureRandom());
        KeyPair keyPair = keyGen.generateKeyPair();

        // Use Netty's SelfSignedCertificate for simplicity
        io.netty.handler.ssl.util.SelfSignedCertificate ssc =
            new io.netty.handler.ssl.util.SelfSignedCertificate("Hytale Proxy");

        // Copy the generated cert and key to our paths
        Files.copy(ssc.certificate().toPath(), Paths.get(certPath),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.copy(ssc.privateKey().toPath(), Paths.get(keyPath),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        LOGGER.info("Self-signed certificate generated: {}", certPath);
        LOGGER.info("Private key generated: {}", keyPath);
    }
}

