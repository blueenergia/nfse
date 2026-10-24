package io.github.t3wv.nfse.server.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AssociadoDTO {
    private String cpfCnpj;
    private String nome;
    private String email;
    private String telefone;
    private EnderecoDTO endereco;

    public String getCpfCnpj() { return cpfCnpj; }
    public void setCpfCnpj(String cpfCnpj) { this.cpfCnpj = cpfCnpj; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }

    public EnderecoDTO getEndereco() { return endereco; }
    public void setEndereco(EnderecoDTO endereco) { this.endereco = endereco; }

    public void validar() {
        if (cpfCnpj == null || cpfCnpj.trim().isEmpty()) {
            throw new IllegalArgumentException("CPF ou CNPJ do associado é obrigatório.");
        }
        String docLimpo = cpfCnpj.replaceAll("\\D", "");
        if (docLimpo.length() != 11 && docLimpo.length() != 14) {
            throw new IllegalArgumentException("CPF/CNPJ inválido: deve conter 11 dígitos (CPF) ou 14 dígitos (CNPJ).");
        }
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome ou Razão Social do associado é obrigatório.");
        }
        if (endereco == null) {
            throw new IllegalArgumentException("Endereço completo do associado é obrigatório para emissão da NFS-e.");
        }
        endereco.validar();
    }
}
