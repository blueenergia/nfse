package io.github.t3wv.nfse.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.t3wv.nfse.server.config.AppConfig;
import io.github.t3wv.nfse.server.crypto.CryptoUtils;
import io.github.t3wv.nfse.server.db.Database;
import io.github.t3wv.nfse.server.model.EmissaoRequest;
import io.github.t3wv.nfse.server.model.EmissaoResponse;
import io.github.t3wv.nfse.server.service.NFSeEmissionService;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    public static void main(String[] args) {
        AppConfig config = new AppConfig();
        Database database = new Database(config.getDatabaseUrl(), config.getDpsNumeroInicial(), config.getDpsSerie());
        NFSeEmissionService emissionService = new NFSeEmissionService(config, database);

        Javalin app = Javalin.create(cfg -> {
            cfg.showJavalinBanner = false;
        }).start(config.getPort());

        LOGGER.info("==========================================================");
        LOGGER.info("  Blue NFSe Microservice rodando na porta {}", config.getPort());
        LOGGER.info("  Ambiente: {}", config.isAmbienteHomologacao() ? "HOMOLOGAÇÃO" : "PRODUÇÃO");
        LOGGER.info("  Prestador: CNPJ {} (Imperatriz-MA)", config.getPrestadorCnpj());
        LOGGER.info("==========================================================");

        // Health check para Cloudflare Tunnel e monitoramento
        app.get("/health", ctx -> {
            ctx.json(Map.of(
                "status", "UP",
                "servico", "blue-nfse-microservice",
                "ambiente", config.isAmbienteHomologacao() ? "HOMOLOGACAO" : "PRODUCAO",
                "prestador", config.getPrestadorCnpj()
            ));
        });

        // Middleware de Autenticação (X-API-KEY) para rotas /api/*
        app.before("/api/*", ctx -> {
            String apiKeyHeader = ctx.header("X-API-KEY");
            if (config.getApiKey() != null && !config.getApiKey().isEmpty()) {
                if (apiKeyHeader == null || !apiKeyHeader.equals(config.getApiKey())) {
                    ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of(
                        "sucesso", false,
                        "erro", "NAO_AUTORIZADO",
                        "mensagem", "Header X-API-KEY inválido ou ausente."
                    ));
                    ctx.skipRemainingHandlers();
                }
            }
        });

        // Consulta de nota por ID de Lançamento do ERP (Idempotência)
        app.get("/api/nfse/lancamento/{idLancamento}", ctx -> {
            String idLancamento = ctx.pathParam("idLancamento");
            EmissaoResponse existente = database.buscarPorLancamento(idLancamento);
            if (existente != null) {
                ctx.json(existente);
            } else {
                ctx.status(HttpStatus.NOT_FOUND).json(Map.of(
                    "sucesso", false,
                    "idLancamento", idLancamento,
                    "mensagem", "Nenhuma NFS-e autorizada encontrada para este lançamento."
                ));
            }
        });

        // Endpoint Padrão de Emissão (JSON Seguro com TLS / Cloudflare)
        app.post("/api/nfse/emitir", ctx -> {
            try {
                EmissaoRequest request = OBJECT_MAPPER.readValue(ctx.body(), EmissaoRequest.class);
                EmissaoResponse response = emissionService.emitir(request);

                if (response.isSucesso()) {
                    ctx.status(HttpStatus.OK).json(response);
                } else {
                    ctx.status(HttpStatus.UNPROCESSABLE_CONTENT).json(response);
                }
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Validação rejeitada na emissão: {}", e.getMessage());
                ctx.status(HttpStatus.BAD_REQUEST).json(Map.of(
                    "sucesso", false,
                    "erro", "DADOS_INVALIDOS",
                    "mensagem", e.getMessage()
                ));
            } catch (Exception e) {
                LOGGER.error("Erro interno no processamento da emissão", e);
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of(
                    "sucesso", false,
                    "erro", "ERRO_INTERNO",
                    "mensagem", "Falha interna ao processar nota: " + e.getMessage()
                ));
            }
        });

        // Endpoint com Criptografia de Ponta a Ponta AES-256-GCM
        app.post("/api/nfse/emitir/secure", ctx -> {
            if (config.getEncryptionKey() == null || config.getEncryptionKey().isEmpty()) {
                ctx.status(HttpStatus.NOT_IMPLEMENTED).json(Map.of(
                    "sucesso", false,
                    "erro", "ENCRYPTION_DISABLED",
                    "mensagem", "Variável ENCRYPTION_KEY não configurada no servidor."
                ));
                return;
            }

            try {
                // Descriptografa corpo da requisição
                Map<?, ?> payloadMap = OBJECT_MAPPER.readValue(ctx.body(), Map.class);
                String encryptedPayload = (String) payloadMap.get("payload");

                if (encryptedPayload == null || encryptedPayload.isEmpty()) {
                    throw new IllegalArgumentException("Campo 'payload' criptografado em Base64 é obrigatório.");
                }

                String decryptedJson = CryptoUtils.decrypt(encryptedPayload, config.getEncryptionKey());
                EmissaoRequest request = OBJECT_MAPPER.readValue(decryptedJson, EmissaoRequest.class);

                EmissaoResponse response = emissionService.emitir(request);

                // Criptografa a resposta de volta com AES-256-GCM
                String responseJson = OBJECT_MAPPER.writeValueAsString(response);
                String encryptedResponse = CryptoUtils.encrypt(responseJson, config.getEncryptionKey());

                ctx.status(response.isSucesso() ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_CONTENT)
                   .json(Map.of(
                       "sucesso", response.isSucesso(),
                       "payload", encryptedResponse
                   ));
            } catch (IllegalArgumentException e) {
                ctx.status(HttpStatus.BAD_REQUEST).json(Map.of(
                    "sucesso", false,
                    "erro", "DADOS_INVALIDOS",
                    "mensagem", e.getMessage()
                ));
            } catch (Exception e) {
                LOGGER.error("Erro no fluxo criptografado", e);
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of(
                    "sucesso", false,
                    "erro", "ERRO_CRIPTOGRAFIA",
                    "mensagem", "Falha na descriptografia/processamento: " + e.getMessage()
                ));
            }
        });
    }
}
