# ADR 0017: Limite explícito por registro CSV

- Status: aceito
- Data: 2026-08-26

## Contexto

O backend já evita carregar o arquivo inteiro em memória: o upload vai para disco, o Worker abre o arquivo por stream e o Commons CSV percorre os registros progressivamente. Ainda existia, porém, uma borda importante para o requisito de evitar OOM: um único registro CSV poderia ser patologicamente grande.

Streaming limita o crescimento de memória em função do tamanho total do arquivo, mas não impede sozinho que o parser materialize um campo ou registro individual com dezenas ou centenas de megabytes antes das validações de negócio, como `transaction_id.length() > 64` ou `category.length() > 100`.

A API do Apache Commons CSV 1.14.x permite limitar quantidade de registros processados, mas não oferece uma configuração equivalente para tamanho máximo de coluna ou registro. A proteção precisa, portanto, ocorrer antes da materialização do `CSVRecord`.

## Decisão

O Reader UTF-8 usado pelo parser será envolvido por `CsvRecordLengthLimitingReader`, que acompanha o tamanho de cada registro lógico enquanto os caracteres ainda estão sendo consumidos de forma incremental.

O limite é configurável por `app.csv.max-record-characters`, com valor inicial de `4096` caracteres. O valor é deliberadamente muito maior que o maior registro válido esperado pelo contrato atual, mas pequeno o bastante para impedir crescimento arbitrário de um único registro.

O contador entende aspas CSV suficientes para não confundir uma quebra de linha dentro de um campo quoted com o fim do registro. Aspas escapadas por `""` também não encerram o campo. Assim, dividir um campo gigante em múltiplas linhas físicas não contorna o limite.

Ao ultrapassar o limite, a leitura é interrompida antes que o Commons CSV conclua a materialização do registro. O erro é convertido em `InvalidCsvFileException`, pois se trata de entrada definitivamente inválida, e não de uma falha transitória de infraestrutura que deveria consumir o orçamento de redelivery do RabbitMQ.

A mesma classificação é aplicada a sintaxe CSV inválida reportada por `CSVException` e a sequências UTF-8 malformadas. Esses problemas pertencem ao arquivo recebido e não tendem a se resolver com uma nova entrega. Outras `IOException` originadas do storage continuam propagadas como falha de infraestrutura e podem consumir a redelivery limitada do Worker.

Também será validado no parser que `amount` caiba em `NUMERIC(19,2)`. A escala já era verificada; limitar a precisão evita que uma linha individualmente inválida atravesse a validação e faça o PostgreSQL rejeitar o lote inteiro.

## Por que limitar o registro e não o arquivo

O tamanho total do upload já possui limites HTTP configuráveis e, principalmente, arquivos grandes são requisito do desafio. Reduzir o tamanho máximo do arquivo para proteger heap contrariaria o objetivo de aceitar datasets com milhões de linhas.

O risco restante está na unidade que precisa ser materializada pelo parser de cada vez. Por isso o limite correto é por registro lógico.

## Por que contar caracteres depois do decoder UTF-8

O parser trabalha com `Reader` e Strings; o risco que queremos controlar é a quantidade de caracteres materializados por registro. O `InputStreamReader` continua usando buffers fixos e o arquivo permanece streaming. O limite não pretende substituir o limite de bytes do upload.

## Falha estrutural depois de batches confirmados

O processamento não é atômico no nível do arquivo inteiro. Se um erro estrutural for encontrado depois que batches anteriores já foram commitados, esses registros e seus contadores permanecem duráveis e o job termina como `FAILED`.

Esse comportamento é consequência deliberada da fronteira transacional por lote definida no ADR 0010. Reverter todos os batches exigiria uma transação longa para milhões de linhas ou uma operação compensatória potencialmente cara, propriedades que o desafio não exige. O estado `FAILED` indica que a importação não deve ser interpretada como completa, mesmo que consultas de diagnóstico consigam observar o subconjunto já confirmado.

## Alternativas rejeitadas

- confiar apenas nos limites `VARCHAR`: a alocação já teria acontecido antes de chegar ao PostgreSQL;
- validar `String.length()` somente depois do parse: protege o banco, mas não protege o parser contra registro gigante;
- `Files.readAllLines` ou `BufferedReader.readLine()`: materializaria linhas e quebraria o suporte correto a CSV quoted/multiline;
- tratar sintaxe ou UTF-8 inválidos como falha transitória: repete um arquivo determinístico que continuará inválido;
- reduzir drasticamente `MAX_UPLOAD_SIZE`: conflita com o requisito de ingestão de arquivos grandes;
- trocar a biblioteca CSV apenas por esse limite: custo e risco desproporcionais quando uma barreira pequena antes do parser resolve a propriedade necessária;
- tornar o arquivo inteiro uma única transação apenas para rollback total: aumenta duração transacional e retenção de recursos em um workload de milhões de linhas sem requisito de atomicidade global.

## Consequências

O uso de memória do Worker passa a ser limitado também pelo maior registro permitido, além dos buffers e do batch. Um CSV com registro acima do limite, sintaxe inválida ou codificação UTF-8 inválida é classificado como arquivo inválido, o job converge para `FAILED` e o arquivo temporário é removido pelo fluxo já existente sem uma redelivery desnecessária.

Batches já confirmados antes de uma falha estrutural permanecem persistidos. Eles representam progresso durável para diagnóstico, não uma importação bem-sucedida; a terminalidade e a validade da importação continuam determinadas pelo estado do job.

O valor `4096` é uma política inicial e configurável, não um número de performance. Se o contrato de entrada ganhar campos maiores, o limite deverá ser revisto junto com os limites de domínio e de banco.
