package io.github.t3wv.nfse.server.service;

import io.github.t3wv.nfse.server.model.EmissaoRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class CalculoService {

    public static class ResultadoCalculo {
        public final BigDecimal kwp;
        public final BigDecimal valorServico;
        public final String descricaoServico;

        public ResultadoCalculo(BigDecimal kwp, BigDecimal valorServico, String descricaoServico) {
            this.kwp = kwp;
            this.valorServico = valorServico;
            this.descricaoServico = descricaoServico;
        }
    }

    public static ResultadoCalculo calcular(EmissaoRequest request) {
        // 1. Calcula ou utiliza kWp
        BigDecimal kwp;
        if (request.getKwp() != null && request.getKwp().compareTo(BigDecimal.ZERO) > 0) {
            kwp = request.getKwp().setScale(3, RoundingMode.HALF_UP);
        } else {
            // Formula: metaKwh / (30 * HSP * (1 - perdas / 100))
            BigDecimal hsp = request.getHsp();
            BigDecimal fatorPerdas = BigDecimal.ONE.subtract(
                request.getPerdasPercentual().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
            );
            BigDecimal denominador = BigDecimal.valueOf(30)
                .multiply(hsp)
                .multiply(fatorPerdas);

            if (denominador.compareTo(BigDecimal.ZERO) == 0) {
                denominador = BigDecimal.valueOf(120); // Fallback padrao
            }

            kwp = request.getConsumoKwh().divide(denominador, 3, RoundingMode.HALF_UP);
        }

        // 2. Calcula Valor do Servico da NFS-e (30% da intermediacao)
        BigDecimal fatorIntermediacao = request.getPercentualIntermediacao()
            .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal valorServico = request.getValorTotalPago()
            .multiply(fatorIntermediacao)
            .setScale(2, RoundingMode.HALF_UP);

        // 3. Monta a descricao oficial idêntica ao modelo homologado da Blue
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.forLanguageTag("pt-BR"));
        symbols.setDecimalSeparator(',');
        symbols.setGroupingSeparator('.');
        DecimalFormat dfKwp = new DecimalFormat("0.00", symbols);
        DecimalFormat dfMoeda = new DecimalFormat("#,##0.00", symbols);

        String descricao = String.format(
            "Prestação de serviços de intermediação, gestão e operacionalização de contratos de participação em sistema de geração distribuída de energia, incluindo uso de plataforma digital para monitoramento e otimização do consumo energético. - Porcentagem equivalente a %s kWp de usina fotovoltaica - Valor referente a intermediação do serviços da associação R$ %s",
            dfKwp.format(kwp),
            dfMoeda.format(valorServico)
        );

        return new ResultadoCalculo(kwp, valorServico, descricao);
    }
}
