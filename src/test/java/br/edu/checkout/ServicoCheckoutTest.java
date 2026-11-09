package br.edu.checkout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ServicoCheckoutTest {

    private static final String CLIENTE = "cliente-1";
    private static final String EMAIL = "cliente@exemplo.com";
    private static final String SKU = "SKU-123";

    @Mock
    private EstoqueGateway estoqueGateway;

    @Mock
    private PagamentoGateway pagamentoGateway;

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private Notificador notificador;

    private ServicoCheckout servico;

    @BeforeEach
    void criarServicoReal() {
        servico = new ServicoCheckout(
                estoqueGateway, pagamentoGateway, pedidoRepository, notificador);
    }

    private static SolicitacaoCheckout solicitacao(int quantidade, String preco) {
        return new SolicitacaoCheckout(
                CLIENTE, EMAIL, SKU, quantidade, new BigDecimal(preco));
    }

    // ---------- 1. Entrada inválida (R1) ----------

    @Test
    void deveRejeitarSolicitacaoNulaSemConsultarColaboradores() {
        assertThrows(IllegalArgumentException.class, () -> servico.finalizar(null));
        verifyNoInteractions(
                estoqueGateway, pagamentoGateway, pedidoRepository, notificador);
    }

    static Stream<Arguments> solicitacoesInvalidas() {
        BigDecimal preco = new BigDecimal("10.00");
        return Stream.of(
                Arguments.of("clienteId nulo",
                        new SolicitacaoCheckout(null, EMAIL, SKU, 1, preco)),
                Arguments.of("clienteId vazio",
                        new SolicitacaoCheckout("", EMAIL, SKU, 1, preco)),
                Arguments.of("clienteId em branco",
                        new SolicitacaoCheckout("   ", EMAIL, SKU, 1, preco)),
                Arguments.of("email nulo",
                        new SolicitacaoCheckout(CLIENTE, null, SKU, 1, preco)),
                Arguments.of("email vazio",
                        new SolicitacaoCheckout(CLIENTE, "", SKU, 1, preco)),
                Arguments.of("email em branco",
                        new SolicitacaoCheckout(CLIENTE, "   ", SKU, 1, preco)),
                Arguments.of("sku nulo",
                        new SolicitacaoCheckout(CLIENTE, EMAIL, null, 1, preco)),
                Arguments.of("sku vazio",
                        new SolicitacaoCheckout(CLIENTE, EMAIL, "", 1, preco)),
                Arguments.of("sku em branco",
                        new SolicitacaoCheckout(CLIENTE, EMAIL, "   ", 1, preco)),
                Arguments.of("quantidade zero",
                        new SolicitacaoCheckout(CLIENTE, EMAIL, SKU, 0, preco)),
                Arguments.of("quantidade negativa",
                        new SolicitacaoCheckout(CLIENTE, EMAIL, SKU, -1, preco)),
                Arguments.of("preço nulo",
                        new SolicitacaoCheckout(CLIENTE, EMAIL, SKU, 1, null)),
                Arguments.of("preço zero",
                        new SolicitacaoCheckout(CLIENTE, EMAIL, SKU, 1, BigDecimal.ZERO)),
                Arguments.of("preço negativo",
                        new SolicitacaoCheckout(CLIENTE, EMAIL, SKU, 1,
                                new BigDecimal("-0.01"))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("solicitacoesInvalidas")
    void deveRejeitarEntradaInvalidaSemConsultarColaboradores(
            String cenario, SolicitacaoCheckout solicitacao) {
        assertThrows(IllegalArgumentException.class, () -> servico.finalizar(solicitacao));
        verifyNoInteractions(
                estoqueGateway, pagamentoGateway, pedidoRepository, notificador);
    }

    @Test
    void deveAceitarUmaUnidadeEManterOPrecoComoTotal() {
        BigDecimal total = new BigDecimal("19.90");
        when(estoqueGateway.reservar(SKU, 1)).thenReturn(true);
        when(pagamentoGateway.cobrar(CLIENTE, total))
                .thenReturn(new Pagamento(true, "tx-1"));
        when(pedidoRepository.salvar(any(PedidoConfirmado.class))).thenReturn("PED-1");

        ResultadoCheckout resultado = servico.finalizar(solicitacao(1, "19.90"));

        assertEquals(total, resultado.total());
    }

    @Test
    void deveCalcularTotalUsualEEnviarAoPagamento() {
        BigDecimal esperado = new BigDecimal("59.70");
        when(estoqueGateway.reservar(SKU, 3)).thenReturn(true);
        when(pagamentoGateway.cobrar(CLIENTE, esperado))
                .thenReturn(new Pagamento(true, "tx-1"));
        when(pedidoRepository.salvar(any(PedidoConfirmado.class))).thenReturn("PED-1");

        ResultadoCheckout resultado = servico.finalizar(solicitacao(3, "19.90"));

        assertEquals(esperado, resultado.total());
        verify(pagamentoGateway).cobrar(CLIENTE, esperado);
    }

    @Test
    void deveManterPrecisaoSemArredondar() {
        BigDecimal esperado = new BigDecimal("0.015");
        when(estoqueGateway.reservar(SKU, 3)).thenReturn(true);
        when(pagamentoGateway.cobrar(CLIENTE, esperado))
                .thenReturn(new Pagamento(true, "tx-1"));
        when(pedidoRepository.salvar(any(PedidoConfirmado.class))).thenReturn("PED-1");

        ResultadoCheckout resultado = servico.finalizar(solicitacao(3, "0.005"));

        assertEquals(esperado, resultado.total());
        verify(pagamentoGateway).cobrar(CLIENTE, esperado);
    }

    // ---------- 5. Sem estoque (R3) ----------

    @Test
    void deveLancarExcecaoQuandoNaoHaEstoqueSemEfeitosPosteriores() {
        when(estoqueGateway.reservar(SKU, 2)).thenReturn(false);

        assertThrows(EstoqueInsuficienteException.class,
                () -> servico.finalizar(solicitacao(2, "10.00")));

        verify(estoqueGateway, times(1)).reservar(SKU, 2);
        verify(estoqueGateway, never()).liberar(any(), anyInt());
        verifyNoInteractions(pagamentoGateway, pedidoRepository, notificador);
    }

    // ---------- 6. Pagamento recusado (R4) ----------

    @Test
    void deveLiberarReservaERecusarQuandoPagamentoNaoAprovado() {
        when(estoqueGateway.reservar(SKU, 2)).thenReturn(true);
        when(pagamentoGateway.cobrar(CLIENTE, new BigDecimal("20.00")))
                .thenReturn(new Pagamento(false, null));

        assertThrows(PagamentoRecusadoException.class,
                () -> servico.finalizar(solicitacao(2, "10.00")));

        InOrder ordem = inOrder(estoqueGateway, pagamentoGateway);
        ordem.verify(estoqueGateway).reservar(SKU, 2);
        ordem.verify(pagamentoGateway).cobrar(CLIENTE, new BigDecimal("20.00"));
        ordem.verify(estoqueGateway, times(1)).liberar(SKU, 2);
        verifyNoInteractions(pedidoRepository, notificador);
    }

    // ---------- 7. Falha técnica no pagamento (R5) ----------

    @Test
    void devePropagarMesmaExcecaoELiberarReservaNaFalhaTecnica() {
        FalhaPagamentoException falha = new FalhaPagamentoException("gateway fora do ar");
        when(estoqueGateway.reservar(SKU, 2)).thenReturn(true);
        when(pagamentoGateway.cobrar(CLIENTE, new BigDecimal("20.00"))).thenThrow(falha);

        FalhaPagamentoException lancada = assertThrows(FalhaPagamentoException.class,
                () -> servico.finalizar(solicitacao(2, "10.00")));

        assertSame(falha, lancada);
        InOrder ordem = inOrder(estoqueGateway, pagamentoGateway);
        ordem.verify(estoqueGateway).reservar(SKU, 2);
        ordem.verify(pagamentoGateway).cobrar(CLIENTE, new BigDecimal("20.00"));
        ordem.verify(estoqueGateway, times(1)).liberar(SKU, 2);
        verifyNoInteractions(pedidoRepository, notificador);
    }

    // ---------- 8. Persistência correta (R6) ----------

    @Test
    void deveRegistrarPedidoComTodosOsCamposCorretos() {
        when(estoqueGateway.reservar(SKU, 3)).thenReturn(true);
        when(pagamentoGateway.cobrar(CLIENTE, new BigDecimal("59.70")))
                .thenReturn(new Pagamento(true, "tx-777"));
        when(pedidoRepository.salvar(any(PedidoConfirmado.class))).thenReturn("PED-9");

        servico.finalizar(solicitacao(3, "19.90"));

        ArgumentCaptor<PedidoConfirmado> captor =
                ArgumentCaptor.forClass(PedidoConfirmado.class);
        verify(pedidoRepository, times(1)).salvar(captor.capture());
        PedidoConfirmado pedido = captor.getValue();
        assertEquals(CLIENTE, pedido.clienteId());
        assertEquals(EMAIL, pedido.email());
        assertEquals(SKU, pedido.sku());
        assertEquals(3, pedido.quantidade());
        assertEquals(new BigDecimal("59.70"), pedido.total());
        assertEquals("tx-777", pedido.transacaoId());
    }

    // ---------- 9. Compra aprovada (R6) ----------

    @Test
    void deveConcluirCompraAprovadaNaOrdemCorretaSemLiberarReserva() {
        BigDecimal total = new BigDecimal("59.70");
        when(estoqueGateway.reservar(SKU, 3)).thenReturn(true);
        when(pagamentoGateway.cobrar(CLIENTE, total))
                .thenReturn(new Pagamento(true, "tx-777"));
        when(pedidoRepository.salvar(any(PedidoConfirmado.class))).thenReturn("PED-9");

        ResultadoCheckout resultado = servico.finalizar(solicitacao(3, "19.90"));

        assertEquals("PED-9", resultado.numeroPedido());
        assertEquals(total, resultado.total());
        assertTrue(resultado.notificacaoEnviada());

        InOrder ordem = inOrder(estoqueGateway, pagamentoGateway, pedidoRepository, notificador);
        ordem.verify(estoqueGateway, times(1)).reservar(SKU, 3);
        ordem.verify(pagamentoGateway, times(1)).cobrar(CLIENTE, total);
        ordem.verify(pedidoRepository, times(1)).salvar(any(PedidoConfirmado.class));
        ordem.verify(notificador, times(1)).enviarConfirmacao(EMAIL, "PED-9", total);
        verify(estoqueGateway, never()).liberar(any(), anyInt());
        // cada efeito exatamente uma vez: nada além do que foi verificado acima
        verifyNoMoreInteractions(
                estoqueGateway, pagamentoGateway, pedidoRepository, notificador);
    }

    // ---------- 10. Falha de notificação (R7) ----------

    @Test
    void naoDeveDesfazerCompraQuandoNotificacaoFalha() {
        BigDecimal total = new BigDecimal("59.70");
        when(estoqueGateway.reservar(SKU, 3)).thenReturn(true);
        when(pagamentoGateway.cobrar(CLIENTE, total))
                .thenReturn(new Pagamento(true, "tx-777"));
        when(pedidoRepository.salvar(any(PedidoConfirmado.class))).thenReturn("PED-9");
        doThrow(new FalhaNotificacaoException("smtp indisponível"))
                .when(notificador).enviarConfirmacao(EMAIL, "PED-9", total);

        ResultadoCheckout resultado = servico.finalizar(solicitacao(3, "19.90"));

        assertEquals("PED-9", resultado.numeroPedido());
        assertEquals(total, resultado.total());
        assertFalse(resultado.notificacaoEnviada());
        verify(pagamentoGateway, times(1)).cobrar(CLIENTE, total);
        verify(pedidoRepository, times(1)).salvar(any(PedidoConfirmado.class));
        verify(estoqueGateway, never()).liberar(any(), anyInt());
    }
}