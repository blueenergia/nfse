package io.github.t3wv.nfse.server.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EmissaoRequest {
    private String idLancamento;
    private AssociadoDTO associado;
    private BigDecimal consumoKwh;
    private BigDecimal valorTotalPago;
    private BigDecimal kwp; // Opcional, calculado automaticamente se nulo
    private BigDecimal hsp; // Horas de Sol Pleno (default 5.0)
    private BigDecimal perdasPercentual; // Default 20%
    private BigDecimal percentualIntermediacao; // Default 30%

    public String getIdLancamento() { return idLancamento; }
    public void setIdLancamento(String idLancamento) { this.idLancamento = idLancamento; }

    public AssociadoDTO getAssociado() { return associado; }
    public void setAssociado(AssociadoDTO associado) { this.associado = associado; }

    public BigDecimal getConsumoKwh() { return consumoKwh; }
    public void setConsumoKwh(BigDecimal consumoKwh) { this.consumoKwh = consumoKwh; }

    public BigDecimal getValorTotalPago() { return valorTotalPago; }
    public void setValorTotalPago(BigDecimal valorTotalPago) { this.valorTotalPago = valorTotalPago; }

    public BigDecimal getKwp() { return kwp; }
    public void setKwp(BigDecimal kwp) { this.kwp = kwp; }

    public BigDecimal getHsp() { return hsp != null ? hsp : BigDecimal.valueOf(5.0); }
    public void setHsp(BigDecimal hsp) { this.hsp = hsp; }

    public BigDecimal getPerdasPercentual() { return perdasPercentual != null ? perdasPercentual : BigDecimal.valueOf(20.0); }
    public void setPerdasPercentual(BigDecimal perdasPercentual) { this.perdasPercentual = perdasPercentual; }

    public BigDecimal getPercentualIntermediacao() { return percentualIntermediacao != null ? percentualIntermediacao : BigDecimal.valueOf(30.0); }
    public void setPercentualIntermediacao(BigDecimal percentualIntermediacao) { this.percentualIntermediacao = percentualIntermediacao; }

    public void validar() {
        if (idLancamento == null || idLancamento.trim().isEmpty()) {
            throw new IllegalArgumentException("idLancamento é obrigatório.");
        }
        if (associado == null) {
            throw new IllegalArgumentException("Dados do associado são obrigatórios.");
        }
        associado.validar();

        if (valorTotalPago == null || valorTotalPago.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("valorTotalPago deve ser maior que zero.");
        }
        if (consumoKwh == null || consumoKwh.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("consumoKwh deve ser maior que zero.");
        }
    }
}
