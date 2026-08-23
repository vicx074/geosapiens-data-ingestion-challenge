# ADR 0004: Volume temporário para arquivos CSV

- Status: aceito
- Data: 2026-08-23

## Contexto

API e Worker são processos distintos. O Worker precisa ler o arquivo depois que a resposta HTTP terminar, sem mantê-lo em memória.

## Decisão

Gravar o upload progressivamente em um volume Docker compartilhado e remover o arquivo somente depois de o job alcançar estado terminal. O caminho será gerado a partir do identificador do job e não será derivado do nome enviado pelo cliente.

O adaptador grava primeiro um arquivo parcial com nome único e o move atomicamente para a chave final depois do término da cópia. Falta de suporte do filesystem à movimentação atômica será tratada como erro; uma movimentação não atômica não será usada como fallback.

A cópia usa inicialmente um buffer fixo de 64 KiB. Esse valor limita a memória por upload e reduz chamadas de I/O sem crescer com o arquivo; continua sendo uma hipótese ajustável por benchmark, não uma garantia universal de melhor desempenho. Métodos que materializam todo o conteúdo, como `InputStream.readAllBytes()` e `MultipartFile.getBytes()`, não serão usados.

Quando o adaptador HTTP for introduzido, `spring.servlet.multipart.file-size-threshold` permanecerá em `0B` para que o container encaminhe as partes grandes ao disco. Os limites de arquivo e request serão explícitos e definidos a partir do dataset de validação, pois os defaults de 1 MB e 10 MB não atendem ao desafio.

## Alternativas rejeitadas

- Armazenar o CSV no PostgreSQL: amplia tráfego e armazenamento do banco sem beneficiar as consultas.
- Object storage: é apropriado para processos em hosts diferentes, requisito ausente no ambiente local.
- Diretório interno da API: não é compartilhado de forma confiável com o Worker.

## Consequências

A solução permanece simples no Compose, mas depende do host e do volume. Alta disponibilidade do arquivo temporário fica explicitamente fora do escopo.

## Referências

- [Propriedades multipart do Spring Boot 3.5](https://docs.spring.io/spring-boot/3.5/appendix/application-properties/)
- [Contrato de `MultipartFile` no Spring Framework 6.2](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/multipart/MultipartFile.html)
- [Contrato de `InputStream`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/InputStream.html)
