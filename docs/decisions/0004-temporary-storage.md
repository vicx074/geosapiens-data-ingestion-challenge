# ADR 0004: Volume temporário para arquivos CSV

- Status: aceito
- Data: 2026-08-23

## Contexto

API e Worker são processos distintos. O Worker precisa ler o arquivo depois que a resposta HTTP terminar, sem mantê-lo em memória.

## Decisão

Gravar o upload progressivamente em um volume Docker compartilhado e remover o arquivo somente depois de o job alcançar estado terminal. O caminho será gerado a partir do identificador do job e não será derivado do nome enviado pelo cliente.

O adaptador grava primeiro um arquivo parcial com nome único e o move atomicamente para a chave final depois do término da cópia. Falta de suporte do filesystem à movimentação atômica será tratada como erro; uma movimentação não atômica não será usada como fallback.

A cópia usa inicialmente um buffer fixo de 64 KiB. Esse valor limita a memória por upload e reduz chamadas de I/O sem crescer com o arquivo; continua sendo uma hipótese ajustável por benchmark, não uma garantia universal de melhor desempenho. Métodos que materializam todo o conteúdo, como `InputStream.readAllBytes()` e `MultipartFile.getBytes()`, não serão usados.

O adaptador HTTP mantém `spring.servlet.multipart.file-size-threshold` em `0B` para que o container encaminhe as partes ao disco antes de expor um `InputStream` à aplicação. Isso preserva memória limitada, embora implique uma gravação temporária adicional do multipart. O caso de uso também não chama `MultipartFile.getBytes()`.

O limite inicial do arquivo é 128 MB. Pelo contrato versionado, cada linha gerada ocupa menos de 64 bytes, portanto o dataset padrão de um milhão de linhas permanece abaixo de 64 MB; o limite comporta mais de duas vezes esse volume sem aceitar uploads ilimitados. O request admite 129 MB para incluir os metadados do multipart. Ambos os limites permanecem configuráveis e deverão ser reavaliados pelo benchmark.

O nome precisa terminar em `.csv`, sem diferenciar maiúsculas de minúsculas. O `Content-Type` informado pelo cliente não é usado como prova do formato porque é opcional e não valida o conteúdo. Cabeçalho, codificação e linhas serão validados progressivamente pelo Worker, onde os erros podem ser associados ao job.

O arquivo final é criado antes da transação que grava job e Outbox. Se essa transação falhar, a API remove o arquivo. Essa ordem impede que um job aceito seja consumido sem arquivo disponível. Uma interrupção do processo exatamente entre as duas etapas pode deixar um arquivo órfão, mas não cria um job inconsistente; uma reconciliação periódica deverá remover órfãos antigos quando o ciclo completo de limpeza for implementado.

## Alternativas rejeitadas

- Armazenar o CSV no PostgreSQL: amplia tráfego e armazenamento do banco sem beneficiar as consultas.
- Object storage: é apropriado para processos em hosts diferentes, requisito ausente no ambiente local.
- Diretório interno da API: não é compartilhado de forma confiável com o Worker.
- Gravar job e Outbox antes do arquivo: abre uma janela na qual o publicador pode entregar ao Worker uma referência cujo CSV ainda não existe.
- Tratar volume e PostgreSQL como uma única transação: o filesystem local não participa da transação do banco; apresentar essa garantia esconderia a janela de falha em vez de resolvê-la.

## Consequências

A solução permanece simples no Compose, mas depende do host e do volume. Alta disponibilidade do arquivo temporário fica explicitamente fora do escopo.

## Referências

- [Propriedades multipart do Spring Boot 3.5](https://docs.spring.io/spring-boot/3.5/appendix/application-properties/)
- [Contrato de `MultipartFile` no Spring Framework 6.2](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/multipart/MultipartFile.html)
- [Contrato de `InputStream`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/InputStream.html)
