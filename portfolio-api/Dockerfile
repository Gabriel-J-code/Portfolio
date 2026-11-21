# =====================================================================
# Imagem da portfolio-api (multi-stage).
#   Construir:  docker build -t portfolio-api .
#   Rodar:      ver a secao "Imagem Docker" do README.
#
# Estagio 1 (build): tem o JDK e o Maven, compila e gera o .jar.
# Estagio 2 (runtime): so o JRE + a aplicacao. O JDK, o Maven, o codigo-fonte
# e o cache de dependencias ficam para tras -> imagem menor e com menos superficie de ataque.
# =====================================================================

# ---------- Estagio 1: build ----------
FROM eclipse-temurin:17-jdk-noble AS build
WORKDIR /build

# Primeiro so o que define as dependencias (wrapper + pom.xml).
# O Docker guarda cada passo em cache: enquanto o pom.xml nao mudar, o download
# das dependencias (passo mais lento) e reaproveitado e so o codigo e recompilado.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# Agora o codigo. Os testes NAO rodam aqui: eles precisam do Docker (Testcontainers),
# que nao existe dentro do build. Eles rodam antes, na maquina ou no CI (GitHub Actions).
COPY src/ src/
RUN ./mvnw -B -q package -DskipTests \
    && cp target/portfolio-api-*.jar application.jar

# Separa o .jar em camadas (dependencias de terceiros, loader do Spring, codigo da aplicacao).
# No estagio 2 cada uma vira uma camada da imagem: ao mudar so o codigo, apenas a
# camada "application" (poucos KB) muda; as dependencias (~dezenas de MB) ficam em cache.
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ---------- Estagio 2: runtime ----------
FROM eclipse-temurin:17-jre-noble
WORKDIR /app

# Nao rodar como root: se alguem explorar uma falha na API, nao ganha root no container
RUN groupadd --system spring && useradd --system --gid spring --no-create-home spring

# Da camada que menos muda para a que mais muda (melhor aproveitamento do cache)
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./

# Perfil de producao por padrao (Swagger desligado, e-mail pelo Resend, sem padroes de dev).
# Pode ser trocado na hora de rodar: docker run -e SPRING_PROFILES_ACTIVE=...
ENV SPRING_PROFILES_ACTIVE=prod

# Opcoes da JVM, pensadas para o Render free (512 MB de RAM e so 0,1 de CPU):
# - MaxRAMPercentage=60: heap de ate ~300 MB. O padrao da JVM (25%) seria pouco; 75% somado ao que a
#   JVM usa fora do heap (metaspace, threads, ~150 MB) poderia passar de 512 MB e o container seria morto.
# - TieredStopAtLevel=1: so o compilador JIT rapido (C1). O C2 gera codigo mais otimizado, mas gasta muita
#   CPU compilando; com 0,1 de CPU isso dobrava o tempo de startup (medido: 286 s -> 136 s).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:TieredStopAtLevel=1"

# CDS (Class Data Sharing): uma execucao de "treino" durante o build grava em application.jsa as classes
# ja carregadas e verificadas; no startup real a JVM so mapeia esse arquivo, em vez de ler e verificar
# milhares de classes de dentro dos .jar. Tecnica documentada pelo Spring Boot para startup mais rapido.
# - spring.context.exit=onRefresh: sobe o contexto do Spring e encerra (o seed, um ApplicationRunner, nem roda).
# - Sem banco no build: Flyway desligado e Hibernate sem consultar o banco (dialeto informado).
# - Variaveis obrigatorias do perfil prod com valores ficticios: valem so para este RUN e nao ficam na imagem.
RUN DB_URL=jdbc:postgresql://treino.invalid/portfolio DB_USER=treino DB_PASSWORD=treino \
    ADMIN_EMAIL=treino@exemplo.com ADMIN_PASSWORD=treino \
    JWT_SECRET=dHJlaW5vLXRyZWluby10cmVpbm8tdHJlaW5vLTMyYiE= \
    RESEND_API_KEY=re_treino MAIL_FROM=treino@exemplo.com CORS_ORIGINS=https://treino.invalid \
    java -XX:ArchiveClassesAtExit=application.jsa -Dspring.context.exit=onRefresh \
         -Dspring.flyway.enabled=false \
         -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
         -Dspring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
         -Dspring.jpa.hibernate.ddl-auto=none \
         -jar application.jar

USER spring

# Documenta a porta (a hospedagem informa a real pela variavel PORT; padrao 8080)
EXPOSE 8080

# Forma "exec" (JSON): o java vira o processo principal e recebe o SIGTERM do
# Docker/da hospedagem, permitindo desligar de forma limpa.
ENTRYPOINT ["java", "-XX:SharedArchiveFile=application.jsa", "-jar", "application.jar"]
