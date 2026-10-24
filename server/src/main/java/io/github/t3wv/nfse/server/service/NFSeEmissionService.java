package io.github.t3wv.nfse.server.service;

import io.github.t3wv.nfse.NFSeConfig;
import io.github.t3wv.nfse.nacional.WSFacade;
import io.github.t3wv.nfse.nacional.classes.nfsenacional.*;
import io.github.t3wv.nfse.server.config.AppConfig;
import io.github.t3wv.nfse.server.db.Database;
import io.github.t3wv.nfse.server.model.AssociadoDTO;
import io.github.t3wv.nfse.server.model.EmissaoRequest;
import io.github.t3wv.nfse.server.model.EmissaoResponse;
import io.github.t3wv.nfse.server.model.EnderecoDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NFSeEmissionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(NFSeEmissionService.class);

    private final AppConfig appConfig;
    private final Database database;
    private WSFacade facade;

    public NFSeEmissionService(AppConfig appConfig, Database database) {
        this.appConfig = appConfig;
        this.database = database;
        inicializarFacade();
    }

    private void inicializarFacade() {
        try {
            File certFile = new File(appConfig.getCertificadoPath());
            File cacertsFile = new File(appConfig.getCadeiaCertificadosPath());

            if (!certFile.exists() || !cacertsFile.exists()) {
                LOGGER.warn("Certificados fiscais não encontrados em {} ou {}. O serviço responderá em modo de simulação/homologação.",
                        appConfig.getCertificadoPath(), appConfig.getCadeiaCertificadosPath());
                return;
            }

            NFSeConfig nfseConfig = new NFSeConfig(
                    appConfig.getCertificadoPath(),
                    appConfig.getCertificadoSenha(),
                    appConfig.getCadeiaCertificadosPath(),
                    appConfig.getCadeiaCertificadosSenha(),
                    appConfig.isAmbienteHomologacao()
            );

            this.facade = new WSFacade(nfseConfig);
            LOGGER.info("WSFacade da SEFIN Nacional inicializado com sucesso (Homologação={}).", appConfig.isAmbienteHomologacao());
        } catch (Exception e) {
            LOGGER.error("Erro ao inicializar conexão SEFIN com certificados", e);
        }
    }

    public EmissaoResponse emitir(EmissaoRequest request) {
        // 1. Validações preliminares
        request.validar();

        // 2. Idempotência: Se o lançamento já foi emitido com sucesso anteriormente, devolve a mesma nota
        EmissaoResponse existente = database.buscarPorLancamento(request.getIdLancamento());
        if (existente != null) {
            LOGGER.info("Lançamento {} já possui nota emitida (Chave {}). Retornando registro existente.",
                    request.getIdLancamento(), existente.getChaveAcesso());
            return existente;
        }

        // 3. Executa os cálculos de negócio da Blue (kWp e 30% da taxa de intermediação)
        CalculoService.ResultadoCalculo calculo = CalculoService.calcular(request);

        // 4. Obtém o próximo número de DPS atômico
        long numeroDps = database.obterProximoNumeroDps(appConfig.getDpsSerie());

        LOGGER.info("Iniciando emissão DPS {} série {} para lançamento {} - Associado: {} - Valor Serviço (30%): R$ {}",
                numeroDps, appConfig.getDpsSerie(), request.getIdLancamento(),
                request.getAssociado().getNome(), calculo.valorServico);

        // 5. Monta a DPS Nacional
        NFSeSefinNacionalDPS dps = montarDPS(request, calculo, numeroDps);

        // 6. Transmissão para a SEFIN
        if (this.facade == null) {
            // Caso os certificados ainda não tenham sido montados na pasta /certs
            String msgSimulada = "Certificado A1 da Blue (.pfx) não montado na pasta /certs. Emissão simulada com sucesso para testes locais.";
            String numNotaSimulada = String.valueOf(numeroDps);
            String chaveSimulada = "210530" + appConfig.getPrestadorCnpj() + "0000000000" + numeroDps + "0000000000";

            database.salvarNota(
                    request.getIdLancamento(), numNotaSimulada, chaveSimulada,
                    numeroDps, appConfig.getDpsSerie(), "AUTORIZADA",
                    request.getAssociado().getCpfCnpj(), request.getAssociado().getNome(),
                    request.getValorTotalPago(), calculo.valorServico, calculo.kwp,
                    "<xmlSimulado/>", null
            );

            return EmissaoResponse.sucesso(
                    request.getIdLancamento(), numNotaSimulada, chaveSimulada,
                    numeroDps, appConfig.getDpsSerie(), calculo.valorServico,
                    request.getValorTotalPago(), calculo.kwp, ZonedDateTime.now(),
                    List.of(msgSimulada)
            );
        }

        try {
            Map.Entry<Integer, Object> resposta = facade.emitirNFSe(dps);
            int statusCode = resposta.getKey();

            if (statusCode == HttpURLConnection.HTTP_CREATED && resposta.getValue() instanceof NFSeSefinNacionalNFSePostResponseSucesso sucesso) {
                String chaveAcesso = sucesso.getChaveAcesso();
                String numeroNota = null;
                try {
                    var nfseXml = sucesso.getNFSeXMLObject();
                    if (nfseXml != null && nfseXml.getInfNFSe() != null) {
                        numeroNota = nfseXml.getInfNFSe().getnNFSe();
                    }
                } catch (Exception e) {
                    LOGGER.warn("Não foi possível extrair número direto do XML da nota, usando ID da DPS", e);
                }

                if (numeroNota == null || numeroNota.trim().isEmpty()) {
                    numeroNota = String.valueOf(numeroDps);
                }

                List<String> alertas = new ArrayList<>();
                if (sucesso.getAlertas() != null) {
                    for (var a : sucesso.getAlertas()) {
                        alertas.add(a.getCodigo() + " - " + a.getDescricao());
                    }
                }

                database.salvarNota(
                        request.getIdLancamento(), numeroNota, chaveAcesso,
                        numeroDps, appConfig.getDpsSerie(), "AUTORIZADA",
                        request.getAssociado().getCpfCnpj(), request.getAssociado().getNome(),
                        request.getValorTotalPago(), calculo.valorServico, calculo.kwp,
                        sucesso.getNFSeXmlString(), null
                );

                LOGGER.info("NFS-e emitida com sucesso! Lançamento: {} - Nota: {} - Chave: {}",
                        request.getIdLancamento(), numeroNota, chaveAcesso);

                return EmissaoResponse.sucesso(
                        request.getIdLancamento(), numeroNota, chaveAcesso,
                        numeroDps, appConfig.getDpsSerie(), calculo.valorServico,
                        request.getValorTotalPago(), calculo.kwp, sucesso.getDataHoraProcessamento(),
                        alertas
                );
            } else if (resposta.getValue() instanceof NFSeSefinNacionalNFSePostResponseErro erro) {
                StringBuilder erroMsg = new StringBuilder();
                if (erro.getErros() != null) {
                    for (var err : erro.getErros()) {
                        erroMsg.append("[").append(err.getCodigo()).append("] ").append(err.getDescricao()).append("; ");
                    }
                }

                String motivo = erroMsg.toString();
                database.salvarNota(
                        request.getIdLancamento(), null, null,
                        numeroDps, appConfig.getDpsSerie(), "REJEITADA",
                        request.getAssociado().getCpfCnpj(), request.getAssociado().getNome(),
                        request.getValorTotalPago(), calculo.valorServico, calculo.kwp,
                        null, motivo
                );

                LOGGER.error("Rejeição fiscal SEFIN ao emitir DPS {}: {}", numeroDps, motivo);
                return EmissaoResponse.erro(request.getIdLancamento(), "REJEICAO_SEFIN", motivo);
            } else {
                String erroGeral = "Resposta inesperada da SEFIN (Status " + statusCode + "): " + resposta.getValue();
                database.salvarNota(
                        request.getIdLancamento(), null, null,
                        numeroDps, appConfig.getDpsSerie(), "ERRO_HTTP",
                        request.getAssociado().getCpfCnpj(), request.getAssociado().getNome(),
                        request.getValorTotalPago(), calculo.valorServico, calculo.kwp,
                        null, erroGeral
                );
                return EmissaoResponse.erro(request.getIdLancamento(), "ERRO_COMUNICACAO", erroGeral);
            }
        } catch (Exception e) {
            LOGGER.error("Exceção ao comunicar com SEFIN para emissão da DPS {}", numeroDps, e);
            database.salvarNota(
                    request.getIdLancamento(), null, null,
                    numeroDps, appConfig.getDpsSerie(), "FALHA_SISTEMA",
                    request.getAssociado().getCpfCnpj(), request.getAssociado().getNome(),
                    request.getValorTotalPago(), calculo.valorServico, calculo.kwp,
                    null, e.getMessage()
            );
            return EmissaoResponse.erro(request.getIdLancamento(), "FALHA_SISTEMA", e.getMessage());
        }
    }

    private NFSeSefinNacionalDPS montarDPS(EmissaoRequest request, CalculoService.ResultadoCalculo calculo, long numeroDps) {
        AssociadoDTO assoc = request.getAssociado();
        EnderecoDTO end = assoc.getEndereco();
        String docTomador = assoc.getCpfCnpj().replaceAll("\\D", "");

        // Tomador
        NFSeSefinNacionalInfoPessoa tomador = new NFSeSefinNacionalInfoPessoa();
        if (docTomador.length() == 11) {
            tomador.setCPF(docTomador);
        } else {
            tomador.setCNPJ(docTomador);
        }
        tomador.setNome(assoc.getNome());
        if (assoc.getEmail() != null && !assoc.getEmail().trim().isEmpty()) {
            tomador.setEmail(assoc.getEmail().trim());
        }
        if (assoc.getTelefone() != null && !assoc.getTelefone().trim().isEmpty()) {
            tomador.setTelefone(assoc.getTelefone().replaceAll("\\D", ""));
        }

        NFSeSefinNacionalEndereco enderecoTomador = new NFSeSefinNacionalEndereco()
                .setLogradouro(end.getLogradouro())
                .setNumero(end.getNumero())
                .setComplemento(end.getComplemento())
                .setBairro(end.getBairro())
                .setEnderecoNacional(new NFSeSefinNacionalEnderNac()
                        .setCodigoMunicipio(end.getCodigoMunicipio().replaceAll("\\D", ""))
                        .setCEP(end.getCep().replaceAll("\\D", "")));
        tomador.setEndereco(enderecoTomador);

        // Prestador: Blue Energia LTDA (Imperatriz - MA)
        NFSeSefinNacionalInfoPrestador prestador = new NFSeSefinNacionalInfoPrestador()
                .setCNPJ(appConfig.getPrestadorCnpj())
                .setInscricaoMunicipal(appConfig.getPrestadorInscricaoMunicipal())
                .setRegimeTributario(new NFSeSefinNacionalRegTrib()
                        .setOpSimplesNacional(NFSeSefinNacionalRegimeTributarioSituacaoSimplesNacional.OPTANTE_ME_EPP)
                        .setRegimeEspecialTributacao(NFSeSefinNacionalRegimeTributarioRegimeEspecialTributacao.NENHUM));

        // Serviço: Código 10.02.02 / NBS 1.1103.22.00
        NFSeSefinNacionalServ servico = new NFSeSefinNacionalServ()
                .setLocalPrestacao(new NFSeSefinNacionalLocPrest()
                        .setCodigoMunicipio(appConfig.getPrestadorMunicipioIbge()))
                .setCServ(new NFSeSefinNacionalCServ()
                        .setCodigoNacionalTributacaoISSQN("100202")
                        .setCodigoNBS("111032200")
                        .setDescricaoServico(calculo.descricaoServico));

        // Valores e Tributação
        NFSeSefinNacionalInfoValores valores = new NFSeSefinNacionalInfoValores()
                .setValoresServicoPrestado(new NFSeSefinNacionalVServPrest()
                        .setValorServicos(calculo.valorServico))
                .setTributos(new NFSeSefinNacionalInfoTributacao()
                        .setTributosMunicipais(new NFSeSefinNacionalTribMunicipal()
                                .setTributacaoISSQN(NFSeSefinNacionalTribMunicipalTributacaoISSQN.OPERACAO_TRIBUTAVEL)
                                .setTipoRetencaoISSQN(NFSeSefinNacionalTribMunicipalTipoRetencaoISSQN.NAO_RETIDO))
                        .setTributosNacionais(new NFSeSefinNacionalTribFederal()
                                .setPiscofins(new NFSeSefinNacionalTribOutrosPisCofins()
                                        .setCST(NFSeSefinNacionalTribOutrosPisCofinsSituacaoTributaria.CONTRIBUICAO_SEM_INCIDENCIA)))
                        .setTotalTributos(new NFSeSefinNacionalTribTotal()
                                .setIndicadorValorTotalTributos("0")));

        // DPS Completa
        return new NFSeSefinNacionalDPS()
                .setInfDPS(new NFSeSefinNacionalInfDPS()
                        .setTipoAmbiente(appConfig.isAmbienteHomologacao() ?
                                NFSeSefinNacionalTipoAmbiente.HOMOLOGACAO : NFSeSefinNacionalTipoAmbiente.PRODUCAO)
                        .setDataHoraEmissao(ZonedDateTime.of(LocalDateTime.now(), ZoneId.of("-03:00")))
                        .setVersaoApp("Blue NFSe Service 1.0")
                        .setSerie(appConfig.getDpsSerie())
                        .setNumeroDPS(numeroDps)
                        .setDataInicioPrestacaoServico(LocalDate.now())
                        .setTipoEmitente(NFSeSefinNacionalInfDPSTipoEmitente.PRESTADOR)
                        .setCodigoMunicipioEmissao(appConfig.getPrestadorMunicipioIbge())
                        .setPrestador(prestador)
                        .setTomador(tomador)
                        .setServicoPrestado(servico)
                        .setValores(valores));
    }
}
