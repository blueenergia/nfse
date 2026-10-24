package io.github.t3wv.nfse.server.db;

import io.github.t3wv.nfse.server.model.EmissaoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.math.BigDecimal;
import java.sql.*;
import java.time.ZonedDateTime;

public class Database {
    private static final Logger LOGGER = LoggerFactory.getLogger(Database.class);
    private final String url;

    public Database(String url, long numeroInicialDps, String serieDps) {
        this.url = url;
        garantirDiretorioBanco(url);
        inicializarTabelas(numeroInicialDps, serieDps);
    }

    private void garantirDiretorioBanco(String dbUrl) {
        if (dbUrl.startsWith("jdbc:sqlite:")) {
            String caminhoArquivo = dbUrl.substring("jdbc:sqlite:".length());
            File file = new File(caminhoArquivo);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url);
    }

    private void inicializarTabelas(long numeroInicialDps, String serieDps) {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            // Tabela de controle de sequencial da DPS
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS controle_dps (
                    serie TEXT PRIMARY KEY,
                    ultimo_numero INTEGER NOT NULL
                );
            """);

            // Insere sequencial inicial se ainda nao existir
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR IGNORE INTO controle_dps (serie, ultimo_numero) VALUES (?, ?)")) {
                ps.setString(1, serieDps);
                ps.setLong(2, numeroInicialDps - 1);
                ps.executeUpdate();
            }

            // Tabela de historico e auditoria das notas emitidas
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS notas_emitidas (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    id_lancamento TEXT UNIQUE NOT NULL,
                    numero_nota TEXT,
                    chave_acesso TEXT,
                    numero_dps INTEGER NOT NULL,
                    serie_dps TEXT NOT NULL,
                    status TEXT NOT NULL,
                    cpf_cnpj_tomador TEXT,
                    nome_tomador TEXT,
                    valor_total_pago REAL,
                    valor_emitido REAL,
                    kwp REAL,
                    xml_retorno TEXT,
                    mensagem_erro TEXT,
                    criado_em DATETIME DEFAULT CURRENT_TIMESTAMP
                );
            """);

            LOGGER.info("Banco de dados SQLite inicializado com sucesso em {}", url);
        } catch (SQLException e) {
            LOGGER.error("Erro ao inicializar tabelas do banco de dados", e);
            throw new RuntimeException("Falha ao inicializar SQLite", e);
        }
    }

    public synchronized long obterProximoNumeroDps(String serie) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            long proximo;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT ultimo_numero FROM controle_dps WHERE serie = ?")) {
                ps.setString(1, serie);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    proximo = rs.getLong("ultimo_numero") + 1;
                } else {
                    proximo = 1;
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO controle_dps (serie, ultimo_numero) VALUES (?, ?) " +
                    "ON CONFLICT(serie) DO UPDATE SET ultimo_numero = ?")) {
                ps.setString(1, serie);
                ps.setLong(2, proximo);
                ps.setLong(3, proximo);
                ps.executeUpdate();
            }

            conn.commit();
            return proximo;
        } catch (SQLException e) {
            LOGGER.error("Erro ao gerar proximo numero de DPS para serie {}", serie, e);
            throw new RuntimeException("Falha ao obter numero sequencial da DPS", e);
        }
    }

    public EmissaoResponse buscarPorLancamento(String idLancamento) {
        String sql = "SELECT * FROM notas_emitidas WHERE id_lancamento = ? AND status = 'AUTORIZADA'";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, idLancamento);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return EmissaoResponse.sucesso(
                    rs.getString("id_lancamento"),
                    rs.getString("numero_nota"),
                    rs.getString("chave_acesso"),
                    rs.getLong("numero_dps"),
                    rs.getString("serie_dps"),
                    BigDecimal.valueOf(rs.getDouble("valor_emitido")),
                    BigDecimal.valueOf(rs.getDouble("valor_total_pago")),
                    BigDecimal.valueOf(rs.getDouble("kwp")),
                    ZonedDateTime.now(),
                    null
                );
            }
        } catch (SQLException e) {
            LOGGER.error("Erro ao buscar lancamento {}", idLancamento, e);
        }
        return null;
    }

    public void salvarNota(String idLancamento, String numeroNota, String chaveAcesso,
                           long numeroDps, String serieDps, String status,
                           String cpfCnpjTomador, String nomeTomador,
                           BigDecimal valorTotalPago, BigDecimal valorEmitido,
                           BigDecimal kwp, String xmlRetorno, String mensagemErro) {
        String sql = """
            INSERT INTO notas_emitidas (
                id_lancamento, numero_nota, chave_acesso, numero_dps, serie_dps,
                status, cpf_cnpj_tomador, nome_tomador, valor_total_pago,
                valor_emitido, kwp, xml_retorno, mensagem_erro
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id_lancamento) DO UPDATE SET
                numero_nota = excluded.numero_nota,
                chave_acesso = excluded.chave_acesso,
                status = excluded.status,
                xml_retorno = excluded.xml_retorno,
                mensagem_erro = excluded.mensagem_erro;
        """;

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, idLancamento);
            ps.setString(2, numeroNota);
            ps.setString(3, chaveAcesso);
            ps.setLong(4, numeroDps);
            ps.setString(5, serieDps);
            ps.setString(6, status);
            ps.setString(7, cpfCnpjTomador);
            ps.setString(8, nomeTomador);
            ps.setDouble(9, valorTotalPago != null ? valorTotalPago.doubleValue() : 0.0);
            ps.setDouble(10, valorEmitido != null ? valorEmitido.doubleValue() : 0.0);
            ps.setDouble(11, kwp != null ? kwp.doubleValue() : 0.0);
            ps.setString(12, xmlRetorno);
            ps.setString(13, mensagemErro);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Erro ao salvar registro de nota para lancamento {}", idLancamento, e);
        }
    }
}
