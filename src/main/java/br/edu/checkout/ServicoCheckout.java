package br.edu.checkout;

import java.math.BigDecimal;
import java.util.Objects;

public final class ServicoCheckout {

    private final EstoqueGateway estoqueGateway;
    private final PagamentoGateway pagamentoGateway;
    private final PedidoRepository pedidoRepository;
    private final Notificador notificador;

    public ServicoCheckout(
            EstoqueGateway estoqueGateway,
            PagamentoGateway pagamentoGateway,
            PedidoRepository pedidoRepository,
            Notificador notificador) {
        this.estoqueGateway = Objects.requireNonNull(estoqueGateway);
        this.pagamentoGateway = Objects.requireNonNull(pagamentoGateway);
        this.pedidoRepository = Objects.requireNonNull(pedidoRepository);
        this.notificador = Objects.requireNonNull(notificador);
    }

    public ResultadoCheckout finalizar(SolicitacaoCheckout solicitacao) {
        validar(solicitacao);

        BigDecimal total = calcularTotal(solicitacao);
        String sku = solicitacao.sku();
        int quantidade = solicitacao.quantidade();

        if (!estoqueGateway.reservar(sku, quantidade)) {
            throw new EstoqueInsuficienteException();
        }

        Pagamento pagamento;
        try {
            pagamento = pagamentoGateway.cobrar(solicitacao.clienteId(), total);
        } catch (FalhaPagamentoException e) {
            estoqueGateway.liberar(sku, quantidade);
            throw e;
        }

        if (!pagamento.aprovado()) {
            estoqueGateway.liberar(sku, quantidade);
            throw new PagamentoRecusadoException();
        }

        PedidoConfirmado pedido = new PedidoConfirmado(
                solicitacao.clienteId(),
                solicitacao.email(),
                sku,
                quantidade,
                total,
                pagamento.transacaoId());
        String numeroPedido = pedidoRepository.salvar(pedido);

        boolean notificado = true;
        try {
            notificador.enviarConfirmacao(solicitacao.email(), numeroPedido, total);
        } catch (FalhaNotificacaoException e) {
            notificado = false;
        }
        return new ResultadoCheckout(numeroPedido, total, notificado);
    }

    private static void validar(SolicitacaoCheckout solicitacao) {
        if (solicitacao == null) {
            throw new IllegalArgumentException("solicitacao é obrigatória");
        }
        exigirTexto(solicitacao.clienteId(), "clienteId");
        exigirTexto(solicitacao.email(), "email");
        exigirTexto(solicitacao.sku(), "sku");
        if (solicitacao.quantidade() <= 0) {
            throw new IllegalArgumentException("quantidade deve ser positiva");
        }
        BigDecimal preco = solicitacao.precoUnitario();
        if (preco == null || preco.signum() <= 0) {
            throw new IllegalArgumentException("precoUnitario deve ser positivo");
        }
    }

    private static void exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " é obrigatório");
        }
    }

    private static BigDecimal calcularTotal(SolicitacaoCheckout solicitacao) {
        return solicitacao.precoUnitario()
                .multiply(BigDecimal.valueOf(solicitacao.quantidade()));
    }
}
