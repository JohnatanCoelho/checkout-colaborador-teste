# Entrega — Checkout com colaboradores simulados

## 1. Registro dos ciclos Red–Green–Refactor

> Preencha com o que você realmente executou. Não reconstrua depois.

### Ciclo 1 — solicitação nula (R1)
- **Teste:** `deveRejeitarSolicitacaoNulaSemConsultarColaboradores`
- **Falha observada:** `finalizar` lançava `UnsupportedOperationException` em vez de `IllegalArgumentException`.
- **Mudança mínima:** `if (solicitacao == null) throw new IllegalArgumentException(...)`.
- **Refatoração:** nenhuma necessária (um único `if`, sem duplicação). Removi um import incorreto (`IllegalSelectorException`) adicionado pela IDE.
- **Resultado:** `Tests run: 1, Failures: 0` — BUILD SUCCESS.

### Ciclo 2 — entradas inválidas parametrizadas (R1)
- **Teste:** `deveRejeitarEntradaInvalidaSemConsultarColaboradores` (inicialmente com 3 casos de `clienteId`).
- **Falha observada:** `UnsupportedOperationException` nos casos novos, `BUILD FAILURE` com test failures. *(confira o `Tests run:` no seu terminal e anote aqui)*
- **Mudança mínima:** *(preencha: validação de `clienteId` nulo ou em branco)*
- **Refatoração:** *(preencha: por exemplo, extrair `exigirTexto` e `validar` depois de validar todos os campos)*

### Ciclo 3 — *(preencha com o próximo ciclo que você fizer de verdade)*
- **Teste:**
- **Falha observada:**
- **Mudança mínima:**
- **Refatoração (ou justificativa):**

## 2. Perguntas

**O que foi mockado e por quê?**
Os quatro colaboradores: `EstoqueGateway`, `PagamentoGateway`, `PedidoRepository` e `Notificador`. Representam sistemas externos (estoque, gateway de pagamento, banco, e-mail). Mockar permite testar só as decisões do serviço, sem rede nem banco, e simular situações difíceis de provocar de verdade (pagamento recusado, falha técnica, falha de notificação). O serviço, os records e o `BigDecimal` são reais.

**Qual teste verifica estado e qual verifica interação?**
Estado (resultado): `deveCalcularTotalUsualEEnviarAoPagamento` e `deveManterPrecisaoSemArredondar` conferem o `total` devolvido; `deveConcluirCompraAprovada...` confere `numeroPedido`, `total` e `notificacaoEnviada`. Interação (colaboração): `deveLancarExcecaoQuandoNaoHaEstoque...` (nada além da reserva é chamado), `deveLiberarReservaERecusar...` (liberação única após cobrar) e a ordem dos quatro efeitos na compra aprovada via `InOrder`.

**Que defeito seria revelado pelo captor?**
O `ArgumentCaptor` mostra o `PedidoConfirmado` realmente entregue ao repositório. Revelaria um pedido montado com campo errado ou trocado (e-mail no lugar do cliente, quantidade errada, total sem cálculo ou arredondado, `transacaoId` incorreto). Verificar só que `salvar` recebeu "qualquer objeto" não pegaria nenhum desses defeitos.

**Por que verificar a ordem aqui não é apenas verificar um detalhe de implementação?**
Porque a ordem é regra de negócio observável por sistemas externos: cobrar antes de reservar poderia cobrar o cliente sem estoque; liberar antes de cobrar desfaria a reserva de uma compra que seria paga; notificar antes de salvar enviaria um número de pedido que ainda não existe. A ordem muda efeitos reais, não só a organização interna do código.

## 3. Como rodar
```
mvn test
```
