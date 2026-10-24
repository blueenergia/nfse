package io.github.t3wv.nfse.server.config;

public class AppConfig {
    private final int port;
    private final String certificadoPath;
    private final String certificadoSenha;
    private final String cadeiaCertificadosPath;
    private final String cadeiaCertificadosSenha;
    private final boolean ambienteHomologacao;
    private final String prestadorCnpj;
    private final String prestadorInscricaoMunicipal;
    private final String prestadorMunicipioIbge;
    private final String dpsSerie;
    private final long dpsNumeroInicial;
    private final String databaseUrl;
    private final String apiKey;
    private final String encryptionKey;

    public AppConfig() {
        this.port = Integer.parseInt(getEnvOrDefault("PORT", "8080"));
        this.certificadoPath = getEnvOrDefault("CERTIFICADO_PATH", "/certs/certificado.pfx");
        this.certificadoSenha = getEnvOrDefault("CERTIFICADO_SENHA", "senha");
        this.cadeiaCertificadosPath = getEnvOrDefault("CADEIA_CERTIFICADOS_PATH", "/certs/cacerts.jks");
        this.cadeiaCertificadosSenha = getEnvOrDefault("CADEIA_CERTIFICADOS_SENHA", "senha");
        this.ambienteHomologacao = Boolean.parseBoolean(getEnvOrDefault("AMBIENTE_HOMOLOGACAO", "true"));

        // Dados oficiais da Blue Energia (Imperatriz - MA)
        this.prestadorCnpj = getEnvOrDefault("PRESTADOR_CNPJ", "62967464000129").replaceAll("\\D", "");
        this.prestadorInscricaoMunicipal = getEnvOrDefault("PRESTADOR_IM", "428469638");
        this.prestadorMunicipioIbge = getEnvOrDefault("PRESTADOR_MUNICIPIO_IBGE", "2105302");
        this.dpsSerie = getEnvOrDefault("DPS_SERIE", "70000");
        this.dpsNumeroInicial = Long.parseLong(getEnvOrDefault("DPS_NUMERO_INICIAL", "218"));

        this.databaseUrl = getEnvOrDefault("DATABASE_URL", "jdbc:sqlite:/data/nfse.db");
        this.apiKey = getEnvOrDefault("API_KEY", "blue-nfse-secret-key");
        this.encryptionKey = getEnvOrDefault("ENCRYPTION_KEY", "");
    }

    private static String getEnvOrDefault(String key, String def) {
        String val = System.getenv(key);
        return (val != null && !val.trim().isEmpty()) ? val.trim() : def;
    }

    public int getPort() { return port; }
    public String getCertificadoPath() { return certificadoPath; }
    public String getCertificadoSenha() { return certificadoSenha; }
    public String getCadeiaCertificadosPath() { return cadeiaCertificadosPath; }
    public String getCadeiaCertificadosSenha() { return cadeiaCertificadosSenha; }
    public boolean isAmbienteHomologacao() { return ambienteHomologacao; }
    public String getPrestadorCnpj() { return prestadorCnpj; }
    public String getPrestadorInscricaoMunicipal() { return prestadorInscricaoMunicipal; }
    public String getPrestadorMunicipioIbge() { return prestadorMunicipioIbge; }
    public String getDpsSerie() { return dpsSerie; }
    public long getDpsNumeroInicial() { return dpsNumeroInicial; }
    public String getDatabaseUrl() { return databaseUrl; }
    public String getApiKey() { return apiKey; }
    public String getEncryptionKey() { return encryptionKey; }
}
