# ==========================================
# Estágio 1: Build do JAR com Maven
# ==========================================
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

# 1. Copia o pom.xml e código fonte da biblioteca base (nfse)
COPY pom.xml .
COPY src ./src
RUN mvn clean install -DskipTests

# 2. Copia o pom.xml e código do servidor microserviço
WORKDIR /app/server
COPY server/pom.xml .
COPY server/src ./src
RUN mvn clean package -DskipTests

# ==========================================
# Estágio 2: Imagem final de execução (multi-arch Apple Silicon & x86_64)
# ==========================================
FROM eclipse-temurin:17-jre
WORKDIR /app

# Cria diretórios de dados persistentes e certificados
RUN mkdir -p /app/data /certs

# Copia o Uber JAR gerado
COPY --from=builder /app/server/target/nfse-server.jar /app/nfse-server.jar

# Variáveis padrão
ENV PORT=8080 \
    DATABASE_URL=jdbc:sqlite:/app/data/nfse.db \
    CERTIFICADO_PATH=/certs/certificado.pfx \
    CADEIA_CERTIFICADOS_PATH=/certs/cacerts.jks \
    AMBIENTE_HOMOLOGACAO=true

EXPOSE 8080

ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-jar", "/app/nfse-server.jar"]
