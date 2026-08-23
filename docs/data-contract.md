# Contrato do arquivo CSV

O desafio não fornece um dataset nem um schema fechado. Este contrato define o menor conjunto de campos que permite demonstrar listagem paginada e soma de valores por categoria e mês sem criar colunas sem uso conhecido.

## Formato

- codificação UTF-8;
- separador por vírgula;
- primeira linha obrigatoriamente contém o cabeçalho;
- terminador de linha `LF`;
- campos seguem as regras de escape do formato CSV;
- cabeçalho e ordem exatos: `transaction_id,occurred_at,amount,category`.

## Colunas

| Coluna | Formato | Necessidade |
|---|---|---|
| `transaction_id` | Texto não vazio com até 64 caracteres | Identificação da transação na origem e exibição na listagem |
| `occurred_at` | Data e hora ISO 8601 em UTC | Filtro e agregação mensal sem depender do fuso do servidor |
| `amount` | Decimal com duas casas, diferente de zero | Soma financeira exata sem aritmética de ponto flutuante |
| `category` | Texto não vazio com até 100 caracteres | Agrupamento exigido para o dashboard |

O número da linha não aparece no CSV como coluna. O Worker o contará durante a leitura e o persistirá como `source_row`, formando com `import_id` a chave de idempotência do reprocessamento.

## Dados gerados

O gerador produz identificadores no formato `txn-000000000001`, timestamps distribuídos entre 2024-01-01 e 2025-12-31, valores entre -10000,00 e 10000,00 e oito categorias em português. Valores negativos permitem representar saídas; positivos permitem representar entradas e estornos.

Não foi adicionada uma descrição livre porque ela não participa de nenhum requisito, filtro ou agregação do desafio. Novas colunas só devem entrar quando existir uma consulta ou regra que as utilize.

## Linhas inválidas

Uma linha que não respeite o número de colunas, os limites ou os formatos será rejeitada individualmente. O erro deverá registrar `source_row`, código e motivo, sem interromper as demais linhas do arquivo.
