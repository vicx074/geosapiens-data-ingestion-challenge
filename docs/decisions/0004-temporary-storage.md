# ADR 0004: Volume temporário para arquivos CSV

- Status: aceito
- Data: 2026-08-23

## Contexto

API e Worker são processos distintos. O Worker precisa ler o arquivo depois que a resposta HTTP terminar, sem mantê-lo em memória.

## Decisão

Gravar o upload progressivamente em um volume Docker compartilhado e remover o arquivo somente depois de o job alcançar estado terminal. O caminho será gerado pela aplicação e não derivado diretamente do nome enviado pelo cliente.

## Alternativas rejeitadas

- Armazenar o CSV no PostgreSQL: amplia tráfego e armazenamento do banco sem beneficiar as consultas.
- Object storage: é apropriado para processos em hosts diferentes, requisito ausente no ambiente local.
- Diretório interno da API: não é compartilhado de forma confiável com o Worker.

## Consequências

A solução permanece simples no Compose, mas depende do host e do volume. Alta disponibilidade do arquivo temporário fica explicitamente fora do escopo.
