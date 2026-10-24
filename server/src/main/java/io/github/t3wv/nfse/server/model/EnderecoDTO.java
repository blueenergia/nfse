package io.github.t3wv.nfse.server.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EnderecoDTO {
    private String logradouro;
    private String numero;
    private String complemento;
    private String bairro;
    private String codigoMunicipio; // Código IBGE (ex: 2105302)
    private String uf;
    private String cep;

    public String getLogradouro() { return logradouro; }
    public void setLogradouro(String logradouro) { this.logradouro = logradouro; }

    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }

    public String getComplemento() { return complemento; }
    public void setComplemento(String complemento) { this.complemento = complemento; }

    public String getBairro() { return bairro; }
    public void setBairro(String bairro) { this.bairro = bairro; }

    public String getCodigoMunicipio() { return codigoMunicipio; }
    public void setCodigoMunicipio(String codigoMunicipio) { this.codigoMunicipio = codigoMunicipio; }

    public String getUf() { return uf; }
    public void setUf(String uf) { this.uf = uf; }

    public String getCep() { return cep; }
    public void setCep(String cep) { this.cep = cep; }

    public void validar() {
        if (logradouro == null || logradouro.trim().isEmpty()) {
            throw new IllegalArgumentException("Logradouro (rua/avenida) do associado é obrigatório.");
        }
        if (numero == null || numero.trim().isEmpty()) {
            throw new IllegalArgumentException("Número do endereço do associado é obrigatório (use 'S/N' se não houver).");
        }
        if (bairro == null || bairro.trim().isEmpty()) {
            throw new IllegalArgumentException("Bairro do associado é obrigatório.");
        }
        if (cep == null || cep.replaceAll("\\D", "").length() != 8) {
            throw new IllegalArgumentException("CEP do associado é obrigatório e deve ter 8 dígitos.");
        }
        if (codigoMunicipio == null || codigoMunicipio.replaceAll("\\D", "").length() != 7) {
            throw new IllegalArgumentException("Código IBGE do município do associado é obrigatório (7 dígitos).");
        }
        if (uf == null || uf.trim().length() != 2) {
            throw new IllegalArgumentException("UF do associado é obrigatória (ex: MA, SP).");
        }
    }
}
