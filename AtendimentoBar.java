import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Classe principal que orquestra a simulação do atendimento no bar.
 */
public class AtendimentoBar {

    // Fila de pedidos (thread-safe) onde os clientes colocam pedidos
    // e os garçons os retiram.
    private final BlockingQueue<Pedido> filaDePedidos = new LinkedBlockingQueue<>();
    
    // Contador atômico para o número total de rodadas (viagens à copa)
    private final AtomicInteger rodadasGlobais = new AtomicInteger(0);
    
    // O número máximo de rodadas antes que o bar feche
    private final int MAX_RODADAS;
    
    // Gerenciadores de threads
    private final ExecutorService poolClientes;
    private final ExecutorService poolGarcons;

    public AtendimentoBar(int totalClientes, int numGarcons, int capacidadeGarcons, int numRodadas) {
        this.MAX_RODADAS = numRodadas;
        this.poolClientes = Executors.newFixedThreadPool(totalClientes);
        this.poolGarcons = Executors.newFixedThreadPool(numGarcons);

        // Inicializa e inicia os garçons
        for (int i = 0; i < numGarcons; i++) {
            poolGarcons.submit(new Garcom(i, filaDePedidos, capacidadeGarcons, rodadasGlobais, MAX_RODADAS));
        }

        // Inicializa e inicia os clientes
        for (int i = 0; i < totalClientes; i++) {
            poolClientes.submit(new Cliente(i, filaDePedidos));
        }
    }

    /**
     * Inicia a simulação e aguarda o término.
     */
    public void iniciarSimulacao() {
        // Aguarda os garçons terminarem suas rodadas
        poolGarcons.shutdown(); // Para de aceitar novas tarefas
        try {
            // Aguarda indefinidamente até que todas as rodadas sejam concluídas
            if (!poolGarcons.awaitTermination(1, TimeUnit.HOURS)) {
                System.err.println("Tempo de simulação excedido.");
                poolGarcons.shutdownNow();
            }
        } catch (InterruptedException e) {
            System.err.println("Simulação (garçons) interrompida.");
            poolGarcons.shutdownNow();
        }

        // Quando os garçons terminam, o bar fecha.
        System.out.println("\n--- O BAR FECHOU (Total de " + rodadasGlobais.get() + " rodadas servidas) ---");
        
        // Interrompe todos os clientes (que podem estar esperando ou consumindo)
        poolClientes.shutdownNow(); // Envia InterruptedException para todos os clientes
        try {
            poolClientes.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            System.err.println("Simulação (clientes) interrompida.");
        }

        System.out.println("--- SIMULAÇÃO ENCERRADA ---");
    }

    /**
     * Ponto de entrada principal
     */
    public static void main(String[] args) {
        // --- Parâmetros da Simulação ---
        int totalClientes = 10;     // a. Quantidade de clientes
        int numGarcons = 3;         // b. Quantidade de garçons
        int capacidadeGarcons = 4;  // c. Capacidade (C) de cada garçom
        int numRodadas = 15;        // d. Número total de rodadas a serem servidas
        // ---------------------------------

        System.out.println(String.format(
            "Iniciando simulação: %d Clientes, %d Garçons (Capacidade %d), %d Rodadas totais.",
            totalClientes, numGarcons, capacidadeGarcons, numRodadas
        ));

        AtendimentoBar bar = new AtendimentoBar(totalClientes, numGarcons, capacidadeGarcons, numRodadas);
        bar.iniciarSimulacao();
    }
}

/**
 * Representa um único pedido de um cliente.
 * Contém um Semáforo para que o cliente possa esperar (acquire)
 * e o garçom possa notificá-lo (release).
 */
class Pedido {
    private final int clienteId;
    // Semáforo (0) - bloqueia o cliente até que o garçom dê 'release'
    private final Semaphore semaforo = new Semaphore(0);

    public Pedido(int clienteId) {
        this.clienteId = clienteId;
    }

    public int getClienteId() {
        return clienteId;
    }

    public Semaphore getSemaforo() {
        return semaforo;
    }

    public void esperar() throws InterruptedException {
        semaforo.acquire();
    }

    public void entregar() {
        semaforo.release();
    }
}

/**
 * Thread (Runnable) que representa um Cliente.
 */
class Cliente implements Runnable {
    private final int id;
    private final BlockingQueue<Pedido> filaDePedidos;
    private final Random rand = new Random();

    public Cliente(int id, BlockingQueue<Pedido> filaDePedidos) {
        this.id = id;
        this.filaDePedidos = filaDePedidos;
    }

    @Override
    public void run() {
        try {
            // Loop de cliente: faz pedido, espera, consome.
            while (true) {
                // 1. fazPedido()
                Pedido meuPedido = new Pedido(id);
                System.out.println("Cliente " + id + " fez um pedido.");
                filaDePedidos.put(meuPedido); // Coloca o pedido na fila

                // 2. esperaPedido()
                System.out.println("Cliente " + id + " está esperando o pedido...");
                meuPedido.esperar(); // Bloqueia até o garçom entregar

                // 3. recebePedido()
                System.out.println("Cliente " + id + " RECEBEU o pedido!");

                // 4. consomePedido() (tempo aleatório)
                int tempoConsumo = rand.nextInt(3000) + 2000; // 2-5 segundos
                Thread.sleep(tempoConsumo);
                System.out.println("Cliente " + id + " terminou de consumir (levou " + tempoConsumo + "ms).");
                
                // Pausa antes de fazer um novo pedido
                Thread.sleep(rand.nextInt(5000) + 1000); 
            }
        } catch (InterruptedException e) {
            // O bar fechou, a thread foi interrompida
            System.out.println("Cliente " + id + " foi para casa (bar fechou).");
            Thread.currentThread().interrupt(); // Restaura o status de interrupção
        }
    }
}

/**
 * Thread (Runnable) que representa um Garçom.
 */
class Garcom implements Runnable {
    private final int id;
    private final BlockingQueue<Pedido> filaDePedidos;
    private final int capacidade;
    private final AtomicInteger rodadasGlobais;
    private final int MAX_RODADAS;
    private final Random rand = new Random();

    public Garcom(int id, BlockingQueue<Pedido> filaDePedidos, int capacidade, AtomicInteger rodadasGlobais, int maxRodadas) {
        this.id = id;
        this.filaDePedidos = filaDePedidos;
        this.capacidade = capacidade;
        this.rodadasGlobais = rodadasGlobais;
        this.MAX_RODADAS = maxRodadas;
    }

    @Override
    public void run() {
        try {
            // Loop do garçom: continua trabalhando enquanto o bar não atingir o
            // número máximo de rodadas.
            while (rodadasGlobais.get() < MAX_RODADAS) {
                
                // 1. recebeMaximoPedidos()
                List<Pedido> pedidosDaRodada = new ArrayList<>();
                
                // Pega o primeiro pedido (bloqueia a thread até ter ao menos 1)
                System.out.println("Garçom " + id + " está esperando por pedidos.");
                Pedido primeiroPedido = filaDePedidos.take(); 
                pedidosDaRodada.add(primeiroPedido);

                // Pega os pedidos restantes até a capacidade (C)
                // 'drainTo' pega todos os itens disponíveis na fila, 
                // sem bloquear, até o limite de (capacidade - 1).
                filaDePedidos.drainTo(pedidosDaRodada, capacidade - 1);

                int numPedidos = pedidosDaRodada.size();
                System.out.println("Garçom " + id + " pegou " + numPedidos + " pedidos.");

                // 2. registraPedidos() (Simula ida à copa / bartender)
                int tempoPreparo = 1000 + (numPedidos * 250); // Tempo baseado no nº de pedidos
                System.out.println("Garçom " + id + " está na copa (preparando " + numPedidos + " pedidos...)");
                Thread.sleep(tempoPreparo);

                // 3. entregaPedidos()
                System.out.println("Garçom " + id + " está entregando " + numPedidos + " pedidos:");
                for (Pedido pedido : pedidosDaRodada) {
                    System.out.println("\t-> Garçom " + id + " entregando ao Cliente " + pedido.getClienteId());
                    pedido.entregar(); // Libera o semáforo do cliente
                }

                // 4. rodada++
                int rodadaAtual = rodadasGlobais.incrementAndGet();
                System.out.println("Garçom " + id + " completou a rodada. (Rodada global: " + rodadaAtual + "/" + MAX_RODADAS + ")");

                // Pequena pausa para o garçom
                Thread.sleep(500);
            }
        } catch (InterruptedException e) {
            // Se o garçom for interrompido (ex: bar fechando mais cedo)
            System.out.println("Garçom " + id + " foi dispensado (interrompido).");
            Thread.currentThread().interrupt();
        }
        
        System.out.println("Garçom " + id + " terminou o expediente.");
    }
}
