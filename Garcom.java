import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue; // fila bloqueante compartilhada entre clientes e garçons
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger; // AtomicInteger para controlar de forma atômica o contador de rodadas

public class Garcom implements Runnable { 
    private final int id; 
    private final BlockingQueue<Pedido> filaDePedidos; // fila compartilhada onde clientes colocam pedidos
    private final int capacidade; // quantidade máxima de pedidos que o garçom pode carregar por rodada
    private final AtomicInteger rodadasGlobais; // contador atômico do total de rodadas servidas
    private final int MAX_RODADAS; // número máximo de rodadas antes do fechamento do bar
    private final Semaphore startSemaphore; // semáforo para controlar a ordem inicial de start dos garçons
    private final Semaphore nextSemaphore; // semáforo para liberar o próximo garçom na sequência
    private final long timeoutMillis; // timeout (ms) que o garçom espera por pedidos adicionais

    public Garcom(int id, BlockingQueue<Pedido> filaDePedidos, int capacidade, AtomicInteger rodadasGlobais, int maxRodadas,
                  long timeoutMillis, Semaphore startSemaphore, Semaphore nextSemaphore) {
        this.id = id; 
        this.filaDePedidos = filaDePedidos; // referencia à fila de pedidos
        this.capacidade = capacidade; // armazena a capacidade do garçom
        this.rodadasGlobais = rodadasGlobais; // referencia ao contador global de rodadas
        this.MAX_RODADAS = maxRodadas; // armazena o máximo de rodadas configurado
        this.startSemaphore = startSemaphore; // semáforo deste garçom para ordenação de start
        this.nextSemaphore = nextSemaphore; // semáforo do próximo garçom na cadeia
        this.timeoutMillis = timeoutMillis; // timeout configurado para esperar pedidos extras
    }

    @Override
    public void run() { 
        try {
            // espera até que este garçom seja liberado para iniciar (ordem sequencial)
            if (startSemaphore != null) {
                startSemaphore.acquire(); // aguarda permissão
            }
            // libera o próximo garçom na cadeia para que ele possa iniciar
            if (nextSemaphore != null) {
                nextSemaphore.release();
            }

            while (true) { 
                // tenta reservar uma rodada antes de buscar pedidos
                int rodadaReservada = -1; // variável que guarda o número da rodada reservada
                while (true) { // loop para tentar incrementar o contador atômico
                    int atual = rodadasGlobais.get(); // lê o valor atual
                    if (atual >= MAX_RODADAS) { 
                        // não há mais rodadas a servir, encerra o expediente
                        System.out.println("Garçom " + id + " terminou o expediente.");
                        return; // finaliza a thread do garçom
                    }
                    if (rodadasGlobais.compareAndSet(atual, atual + 1)) { 
                        rodadaReservada = atual + 1; 
                        break; 
                    }
                }

                List<Pedido> pedidosDaRodada = new ArrayList<>(); // cria lista para armazenar pedidos desta rodada

                System.out.println("Garçom " + id + " está esperando por pedidos."); 
                Pedido primeiroPedido = filaDePedidos.take(); // bloqueia até receber o primeiro pedido
                pedidosDaRodada.add(primeiroPedido); // adiciona o primeiro pedido à lista

                // tenta coletar pedidos adicionais até a capacidade, aguardando timeoutMillis por cada um
                for (int i = 1; i < capacidade; i++) {
                    Pedido p = filaDePedidos.poll(timeoutMillis, TimeUnit.MILLISECONDS); // tenta pegar um pedido com timeout
                    if (p == null) break; // se o tempo expirar, segue com os pedidos coletados
                    pedidosDaRodada.add(p); // adiciona o pedido coletado
                }

                int numPedidos = pedidosDaRodada.size(); // número de pedidos a serem preparados
                System.out.println("Garçom " + id + " pegou " + numPedidos + " pedidos."); 

                int tempoPreparo = 1000 + (numPedidos * 250); // calcula tempo de preparo baseado no número de pedidos
                System.out.println("Garçom " + id + " está na copa (preparando " + numPedidos + " pedidos...)"); 
                Thread.sleep(tempoPreparo); // simula o tempo de preparo

                System.out.println("Garçom " + id + " está entregando " + numPedidos + " pedidos:"); 
                for (Pedido pedido : pedidosDaRodada) { 
                    System.out.println("\t-> Garçom " + id + " entregando ao Cliente " + pedido.getClienteId()); 
                    pedido.entregar(); // libera o semáforo do cliente para que ele continue
                }

                System.out.println("Garçom " + id + " completou a rodada. (Rodada global: " + rodadaReservada + "/" + MAX_RODADAS + ")"); // log de conclusão da rodada

                Thread.sleep(500); // pequena pausa entre rodadas
            }
        } catch (InterruptedException e) { // captura interrupção da thread
            System.out.println("Garçom " + id + " foi dispensado (interrompido)."); 
            Thread.currentThread().interrupt(); 
        }
    }
}
