package io.github.t3wv.nfse.server.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class EmissaoResponse {
    private boolean sucesso;
    private String idLancamento;
    private String numeroNota;
    private String chaveAcesso;
    private Long numeroDps;
    private String serieDps;
    private BigDecimal valorEmitido;
    private BigDecimal valorTotalPago;
    private BigDecimal kwpCalculado;
    private ZonedDateTime dataProcessamento;
    private String mensagem;
    private String erroCodigo;
    private List<String> alertas;

    public static EmissaoResponse sucesso(String idLancamento, String numeroNota, String chaveAcesso, Long numeroDps, String serieDps, BigDecimal valorEmitido, BigDecimal valorTotalPago, BigDecimal kwpCalculado, ZonedDateTime dataProcessamento, List<String> alertas) {
        EmissaoResponse r = new EmissaoResponse();
        r.sucesso = true;
        r.idLancamento = idLancamento;
        r.numeroNota = numeroNota;
        r.chaveAcesso = chaveAcesso;
        r.numeroDps = numeroDps;
        r.serieDps = serieDps;
        r.valorEmitido = valorEmitido;
        r.valorTotalPago = valorTotalPago;
        r.kwpCalculado = kwpCalculado;
        r.dataProcessamento = dataProcessamento != null ? dataProcessamento : ZonedDateTime.now();
        r.alertas = alertas;
        return r;
    }

    public static EmissaoResponse erro(String idLancamento, String erroCodigo, String mensagem) {
        EmissaoResponse r = new EmissaoResponse();
        r.sucesso = false;
        r.idLancamento = idLancamento;
        r.erroCodigo = erroCodigo;
        r.mensagem = mensagem;
        r.dataProcessamento = ZonedDateTime.now();
        return r;
    }

    public boolean isSucesso() { return sucesso; }
    public void setSucesso(boolean sucesso) { this.sucesso = sucesso; }

    public String getIdLancamento() { return idLancamento; }
    public void setIdLancamento(String idLancamento) { this.idLancamento = idLancamento; }

    public String getNumeroNota() { return numeroNota; }
    public void setNumeroNota(String numeroNota) { this.numeroNota = numeroNota; }

    public String getChaveAcesso() { return chaveAcesso; }
    public void setChaveAcesso(String chaveAcesso) { this.chaveAcesso = chaveAcesso; }

    public Long getNumeroDps() { return numeroDps; }
    public void setNumeroDps(Long numeroDps) { this.numeroDps = numeroDps; }

    public String getSerieDps() { return serieDps; }
    public void setSerieDps(String serieDps) { this.serieDps = serieDps; }

    public BigDecimal getValorEmitido() { return valorEmitido; }
    public void setValorEmitido(BigDecimal valorEmitido) { this.valorEmitido = valorEmitido; }

    public BigDecimal getValorTotalPago() { return valorTotalPago; }
    public void setValorTotalPago(BigDecimal valorTotalPago) { this.valorTotalPago = valorTotalPago; }

    public BigDecimal getKwpCalculado() { return kwpCalculado; }
    public void setKwpCalculado(BigDecimal kwpCalculado) { this.kwpCalculado = kwpCalculado; }

    public ZonedDateTime getDataProcessamento() { return dataProcessamento; }
    public void setDataProcessamento(ZonedDateTime dataProcessamento) { this.dataProcessamento = dataProcessamento; }

    public String getMensagem() { return mensagem; }
    public void setMensagem(String mensagem) { this.mensagem = mensagem; }

    public String getErroCodigo() { return erroCodigo; }
    public void setErroCodigo(String erroCodigo) { this.erroCodigo = erroCodigo; }

    public List<String> getAlertas() { return alertas; }
    public void setAlertas(List<String> alertas) { this.alertas = alertas; }
}
