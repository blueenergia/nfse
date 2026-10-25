package io.github.t3wv.nfse.server.config;

import io.github.t3wv.nfse.NFSeConfig;
import org.apache.commons.lang3.StringUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

public class BlueNFSeConfig implements NFSeConfig {
    private final String certificadoPath;
    private final String certificadoSenha;
    private final String cadeiaCertificadosPath;
    private final String cadeiaCertificadosSenha;
    private final boolean teste;

    private KeyStore keyStoreCertificado = null;
    private KeyStore keyStoreCadeia = null;

    public BlueNFSeConfig(String certificadoPath, String certificadoSenha,
                          String cadeiaCertificadosPath, String cadeiaCertificadosSenha,
                          boolean teste) {
        this.certificadoPath = certificadoPath;
        this.certificadoSenha = certificadoSenha;
        this.cadeiaCertificadosPath = cadeiaCertificadosPath;
        this.cadeiaCertificadosSenha = cadeiaCertificadosSenha;
        this.teste = teste;
    }

    @Override
    public String getCertificadoSenha() {
        return certificadoSenha;
    }

    @Override
    public String getCadeiaCertificadosSenha() {
        return cadeiaCertificadosSenha;
    }

    @Override
    public KeyStore getKeyStoreCertificado() throws KeyStoreException {
        if (this.keyStoreCertificado == null && StringUtils.isNotBlank(this.certificadoPath)) {
            this.keyStoreCertificado = KeyStore.getInstance("PKCS12");
            try (InputStream certificadoStream = new FileInputStream(this.certificadoPath)) {
                this.keyStoreCertificado.load(certificadoStream, this.getCertificadoSenha().toCharArray());
            } catch (CertificateException | NoSuchAlgorithmException | IOException e) {
                this.keyStoreCertificado = null;
                throw new KeyStoreException("Não foi possível carregar o certificado A1 da Blue", e);
            }
        }
        return this.keyStoreCertificado;
    }

    @Override
    public KeyStore getKeyStoreCadeia() throws KeyStoreException {
        if (this.keyStoreCadeia == null && StringUtils.isNotBlank(this.cadeiaCertificadosPath)) {
            this.keyStoreCadeia = KeyStore.getInstance("JKS");
            try (InputStream cadeiaStream = new FileInputStream(this.cadeiaCertificadosPath)) {
                this.keyStoreCadeia.load(cadeiaStream, this.getCadeiaCertificadosSenha().toCharArray());
            } catch (CertificateException | NoSuchAlgorithmException | IOException e) {
                this.keyStoreCadeia = null;
                throw new KeyStoreException("Não foi possível carregar a cadeia de certificados", e);
            }
        }
        return this.keyStoreCadeia;
    }

    @Override
    public boolean isTeste() {
        return teste;
    }

    @Override
    public int getHttpPostTimeoutSegundos() {
        return 60; // Timeout estendido para emissão
    }
}
