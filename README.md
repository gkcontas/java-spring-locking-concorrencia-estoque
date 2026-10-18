# Locking e concorrência num estoque

Dois pedidos chegam no mesmo instante e disputam a mesma linha de estoque. Cada
um lê o saldo, subtrai a sua quantidade e grava o resultado. Os dois recebem
200, o cliente de um deles recebeu mercadoria que nunca saiu da prateleira, e o
banco não reclamou de nada — o saldo final é um número perfeitamente legal.

Esse é o *lost update*. Este projeto o mede em vez de narrá-lo, e compara as
três formas de evitá-lo com o custo de cada uma sob concorrência real: versão
otimista, `SELECT FOR UPDATE` e `UPDATE` condicional no próprio banco.

## Tecnologias

- **Java 21** e **Spring Boot 3.5.3**
- **PostgreSQL 16** com **Flyway** — a coluna `version` e a constraint de saldo
  vêm da migration, não do `hbm2ddl`
- **Spring Retry** — orçamento de tentativas e backoff aleatório do retry
  otimista
- **Testcontainers** — 52 testes de integração contra PostgreSQL real

## O modelo

```
stock_item ──┬── stock_movement   (um por saque aceito)
             └── reservation      (filha, para o furo da versão)
```

`stock_item` tem `quantity`, `version` e `check (quantity >= 0)`.

A constraint é intencional, e o que ela **não** pega é o ponto. Ela impede
vender abaixo de zero. Ela é cega para o lost update: quando duas transações
leem 100 e as duas gravam 99, a linha para num valor que satisfaz todas as
restrições da tabela e ainda assim está uma unidade errado.

`stock_movement` é o que transforma o problema em número. As unidades que a
aplicação prometeu aos clientes são `sum(quantity)`; as unidades que realmente
saíram são a queda em `stock_item.quantity`. A diferença entre as duas é estoque
entregue duas vezes.

## Subindo

```bash
export DOCKER_HOST=unix:///var/run/docker.sock
docker compose up -d
./gradlew bootRun
```

## As cinco estratégias

| Estratégia         | Como baixa o saldo                           | Erro do cliente |
|--------------------|----------------------------------------------|-----------------|
| `NONE`             | lê, subtrai em Java, grava                   | nenhum — mente  |
| `OPTIMISTIC`       | `@Version` no `where`, 409 para quem perdeu  | 409             |
| `OPTIMISTIC_RETRY` | igual, com retry que relê antes de cada vez  | 409 só no fim   |
| `PESSIMISTIC`      | `SELECT ... FOR UPDATE` na mesma consulta    | 423 no timeout  |
| `ATOMIC`           | `UPDATE ... WHERE id = ? AND quantity >= ?`  | 422 se faltar   |

```bash
curl -s -X POST http://localhost:8080/stock-items \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-MANUAL","name":"Coffee beans","quantity":50}'

curl -s -X POST http://localhost:8080/stock-items/SKU-MANUAL/withdrawals \
  -H 'Content-Type: application/json' \
  -d '{"quantity":5,"strategy":"ATOMIC"}'
```

```json
{"sku":"SKU-MANUAL","requested":5,"remaining":45,"strategy":"ATOMIC","attempts":1}
```

Os três códigos de erro são diferentes de propósito. **409** diz "seu pedido
estava certo, alguém chegou na linha primeiro, releia e mande de novo".
**422** diz "não tem estoque, mandar de novo não vai ajudar". **423** diz "a
linha está travada e a espera venceu". Um cliente que recebe 409 nos três casos
ou tenta para sempre ou desiste de um pedido que teria funcionado.

## O lost update, medido

Trinta e dois pedidos simultâneos, uma unidade cada, 5 ms entre a leitura e a
escrita — o tempo que um serviço real gasta validando, chamando outro sistema ou
montando uma resposta.

```bash
curl -s -X POST \
  'http://localhost:8080/scenarios/strategies/NONE?workers=32&initialQuantity=5000&thinkTimeMillis=5'
```

```json
{"strategy":"NONE","workers":32,"unitsPromised":32,"unitsRemoved":1,
 "unitsLost":31,"successes":32,"failures":{},"transactionsUsed":32,
 "elapsedMillis":36,"throughputPerSecond":887.7}
```

Trinta e duas respostas 200, trinta e duas linhas em `stock_movement`, e **uma**
unidade a menos no estoque. Nenhum erro, nenhum log, nenhuma constraint
violada. A coluna `version` existe nessa linha e continua em zero, porque a
escrita sem proteção nunca a coloca no `where` — a checagem otimista é uma
propriedade do comando, não do schema.

### As quatro proteções, mesma carga

| Estratégia                  | Sucessos | Falhas       | Perdidas | Transações | Tempo  | Média    |
|-----------------------------|---------:|--------------|---------:|-----------:|-------:|---------:|
| `NONE`                      |       32 | —            |   **31** |         32 |  36 ms |  21,3 ms |
| `OPTIMISTIC`                |        1 | 31 conflito  |        0 |          1 |  10 ms |   8,5 ms |
| `OPTIMISTIC_RETRY`          |       32 | —            |        0 |    **132** | 145 ms |  50,3 ms |
| `PESSIMISTIC`               |       32 | —            |        0 |         32 | 292 ms | 143,1 ms |
| `ATOMIC`                    |       32 | —            |        0 |         32 |  43 ms |  25,6 ms |
| REPEATABLE READ, sem código |        1 | 31 conflito  |        0 |          1 |  11 ms |   9,7 ms |
| `PESSIMISTIC` com `NOWAIT`  |        1 | 31 travado   |        0 |          1 |   7 ms |   1,7 ms |

As quatro proteções têm a mesma correção e quatro formas completamente
diferentes de entregá-la:

- **`OPTIMISTIC`** é rápida porque atende um pedido e recusa trinta e um. A
  linha fica certa; a carga não foi feita.
- **`OPTIMISTIC_RETRY`** atende todos, gastando **132 transações para 32
  saques**. O número das tentativas fica gravado em `stock_movement.attempts`,
  não só num log.
- **`PESSIMISTIC`** atende todos com exatamente uma transação cada, e paga em
  fila: os 5 ms de cada pedido são tempo com o lock na mão, então 32 pedidos
  custam 32 × 5 ms serializados.
- **`ATOMIC`** atende todos em 43 ms porque os 5 ms acontecem *antes* do único
  comando, e nenhum lock é segurado durante eles.

### Nível de isolamento não é proteção automática

REPEATABLE READ no PostgreSQL é isolamento por snapshot: uma transação que
grava uma linha alterada depois do seu snapshot é abortada com `40001`. O
mesmo código sem nenhuma proteção na aplicação deixa de perder unidades — e
passa a devolver erro para trinta e um dos trinta e dois pedidos.

O buraco fecha movendo o problema. O saldo para de ficar errado e o chamador
ganha a obrigação de reler e tentar de novo, que é exatamente a obrigação que a
versão otimista cria, só que com a transação inteira como unidade de conflito
em vez de uma linha.

## O que custa é a linha quente, não a estratégia

Os mesmos 32 pedidos, sem think time, primeiro todos na mesma linha e depois
espalhados em 32 linhas.

| Estratégia         | 1 linha             | 32 linhas         | Fator |
|--------------------|---------------------|-------------------|------:|
| `NONE`             | 33 ms, 31 perdidas  | 5 ms, 0 perdidas  |  6,6x |
| `OPTIMISTIC`       | 6 ms, 2 sucessos    | 5 ms, 32 sucessos |     — |
| `OPTIMISTIC_RETRY` | 187 ms, 128 transações | 8 ms, 32 transações | 23x |
| `PESSIMISTIC`      | 48 ms               | 5 ms              |  9,6x |
| `ATOMIC`           | 34 ms               | 6 ms              |  5,7x |

Espalhados, os cinco caminhos ficam entre 5 e 8 ms e entre 3.800 e 6.200
pedidos por segundo — indistinguíveis. **A estratégia escolhida não custa
praticamente nada; a linha disputada custa tudo.** Antes de trocar de
estratégia vale perguntar se o estoque precisa mesmo morar numa linha só.

E `NONE` espalhado não perde nada, o que explica por que esse bug sobrevive
anos em produção: ele só aparece quando dois pedidos caem no mesmo SKU no mesmo
instante.

### Escalando na linha quente

| Pedidos | `OPTIMISTIC_RETRY`              | `PESSIMISTIC`        | `ATOMIC`             |
|--------:|---------------------------------|----------------------|----------------------|
|       8 | 26 tx, 55 ms, 145/s             | 8 tx, 9 ms, 817/s    | 8 tx, 12 ms, 637/s   |
|      16 | 58 tx, 108 ms, 148/s            | 16 tx, 19 ms, 814/s  | 16 tx, 21 ms, 739/s  |
|      32 | 119 tx, 110 ms, 291/s           | 32 tx, 37 ms, 861/s  | 32 tx, 36 ms, 882/s  |
|      64 | 281 tx, 257 ms, 249/s, 5 falhas | 64 tx, 76 ms, 842/s  | 64 tx, 77 ms, 822/s  |

Aqui a sabedoria recebida se inverte. O lock pessimista e o `UPDATE` atômico
mantêm throughput estável em torno de 840 pedidos por segundo e **uma**
transação por saque, com a latência crescendo linearmente com o tamanho da
fila. O retry otimista fica preso entre 150 e 290 por segundo e gasta de 3,3 a
4,4 transações por saque — trabalho feito e jogado fora. Na mesma linha,
**esperar na fila sai cerca de três vezes mais barato que tentar de novo**.

E o orçamento de tentativas tem fundo: com 64 pedidos concorrentes, cinco deles
esgotam as oito tentativas e o 409 chega ao cliente de qualquer forma. Um retry
não remove a necessidade de tratar o conflito, ele a torna mais rara.

## As armadilhas

### `OptimisticLockException` não acontece no `save()`

O `where version = ?` não é emitido por nenhuma linha do método de serviço. O
Hibernate monta o `UPDATE` no flush, que sem flush explícito é durante o
commit — depois do método transacional já ter retornado normalmente. Um
`try/catch` em volta da escrita, ou em volta do corpo inteiro, não pega nada:

```bash
curl -s -X POST 'http://localhost:8080/scenarios/catch-inside?workers=4'
```

```json
{"workers":4,"successes":1,"failures":{"conflict":3},"innerCatchFired":false}
```

O método do cenário envolve todo o corpo num `catch` que relançaria um
`IllegalStateException` se algum dia rodasse. Nunca roda. O conflito pertence a
quem está **fora** da fronteira transacional — que também é o único lugar capaz
de tentar de novo, porque um retry é uma transação nova com uma leitura nova.
Repetir com a entidade da tentativa que falhou grava para sempre a mesma versão
velha.

### A versão guarda a linha onde mora, não o invariante

Uma reserva não altera o item: só o lê, para conferir que o pedido cabe no que
sobrou. O Hibernate vê um pai limpo e um filho novo, emite um `INSERT` e nenhum
`UPDATE`, e a checagem de versão que o time acredita estar protegendo a regra
simplesmente não acontece.

```bash
curl -s -X POST 'http://localhost:8080/scenarios/reservations?workers=2&initialQuantity=10&quantityEach=6'
curl -s -X POST 'http://localhost:8080/scenarios/reservations?workers=2&initialQuantity=10&quantityEach=6&forceParentIncrement=true'
```

```json
{"mode":"unguarded","successes":2,"reservedUnits":12,"oversoldUnits":2,"finalVersion":0}
{"mode":"force-increment","successes":1,"failures":{"conflict":1},
 "reservedUnits":6,"oversoldUnits":0,"finalVersion":1}
```

Doze unidades reservadas sobre um saldo de dez, com as duas transações passando
na checagem — e a explicação numa única palavra: `finalVersion: 0`. Ninguém
escreveu no pai. A saída é `OPTIMISTIC_FORCE_INCREMENT` na leitura, que promete
incrementar a versão no flush mesmo sem nada mudar, fazendo do pai o ponto de
serialização das alterações nos filhos.

### Deadlock é consequência da ordem dos locks

Duas transações, duas linhas, e a única variável que decide se o banco mata uma
delas:

```bash
curl -s -X POST http://localhost:8080/scenarios/deadlock/crossed
curl -s -X POST http://localhost:8080/scenarios/deadlock/agreed
```

```json
{"lockOrder":"crossed","successes":1,"deadlocks":1,"elapsedMillis":213}
{"lockOrder":"agreed","successes":2,"deadlocks":0,"elapsedMillis":6}
```

A→B contra B→A fecha um ciclo que o PostgreSQL só rompe abortando um
participante: `40P01`, detectado em 213 ms com `deadlock_timeout` em 200 ms. As
mesmas linhas, as mesmas duas transações e a mesma disputa não falham quando os
dois lados ordenam por chave primária — e nenhum retry foi acrescentado. A
chave primária funciona porque todo participante calcula a mesma ordem sem
precisar combinar com os outros.

### `FOR UPDATE` espera para sempre por padrão

```bash
curl -s -X POST 'http://localhost:8080/scenarios/lock-wait?holdMillis=600'
curl -s -X POST 'http://localhost:8080/scenarios/lock-wait?holdMillis=5000'
```

```json
{"holdMillis":600,"waitingOutcome":"ok","waitingMillis":450,
 "noWaitOutcome":"locked","noWaitMillis":4}
{"holdMillis":5000,"serverLockTimeoutMillis":2000,
 "waitingOutcome":"locked","waitingMillis":2003,"noWaitOutcome":"locked","noWaitMillis":3}
```

`NOWAIT` desiste em 3 ou 4 ms; a espera comum fica 450 ms na fila e consegue o
lock. Quando o detentor segura mais que o `lock_timeout` do servidor, a espera
morre em 2.003 ms com `55P03` — e sem esse ajuste no servidor ela não teria
fim. A requisição não falharia, ela **penduraria**, o que é bem mais difícil de
achar que um erro.

Vale saber que `jakarta.persistence.lock.timeout` com valor positivo não compra
nada no PostgreSQL: não existe `FOR UPDATE WAIT n` na gramática, então o hint
cai no vazio e quem governa a espera é a configuração do servidor. Só `0`, que
virá `NOWAIT`, tem efeito no comando.

### O `UPDATE` atômico não mexe na versão

É o preço de não passar pela entidade. Depois de um saque otimista, um
pessimista e um force increment, a linha está na versão 3; nenhum saque atômico
a move. Uma linha escrita pelos dois caminhos tem uma checagem otimista que
silenciosamente deixa de proteger qualquer coisa, porque a versão que ela
compara não acompanha mais todas as alterações.

O outro preço é menor: o saldo resultante não é conhecido sem perguntar de novo,
porque um `UPDATE` não devolve linha. E a regra de negócio passa a morar no
`where`, o que resolve uma subtração e não resolve um invariante que dependa de
mais de uma linha.

### `@Modifying` passa por cima do contexto de persistência

Um `UPDATE` em massa vai direto ao banco e o contexto de persistência nunca é
avisado, então uma entidade já carregada continuaria respondendo com a
quantidade de antes pelo resto da transação. Por isso o `clearAutomatically` e
o `flushAutomatically` estão lá, e não são enfeite.

## Testes

```bash
export DOCKER_HOST=unix:///var/run/docker.sock
./gradlew test
```

52 testes contra PostgreSQL real, container singleton. Os que precisam de uma
corrida certa em vez de provável usam uma barreira entre a leitura e a escrita,
que é a forma de todo lost update: todos leem antes de qualquer um gravar. Com
`PESSIMISTIC` isso seria um deadlock por construção — o primeiro worker ficaria
parado na barreira segurando o lock de que os outros precisam para chegar nela —
então essas classes usam think time.

As asserções de contenção são sobre correção, nunca sobre tempo. Throughput é
medição, não contrato, e um teste que afirma que uma estratégia é mais rápida
que outra é um teste que falha num laptop ocupado.

## Setup das medições

Todos os números acima vieram de uma única sessão: jar empacotado
(`./gradlew bootJar`), daemon do Gradle parado, PostgreSQL 16-alpine em
container, três rodadas de aquecimento descartadas antes de cada tabela. JVM
Amazon Corretto 21.0.5.

O servidor roda com `max_connections=300`, `lock_timeout=2000` e
`deadlock_timeout=200`; o pool tem 80 conexões, acima do maior número de
workers, para o gargalo ser o lock da linha e não a fila do pool.
