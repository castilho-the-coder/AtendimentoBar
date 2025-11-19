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

public class AtendimentoBar {

    // fila de pedidos onde os clientes colocam pedidos e os garçons os retiram.
    private final BlockingQueue<Pedido> filaDePedidos = new LinkedBlockingQueue<>();
    
    // contador atômico para o número total de rodadas 
    private final AtomicInteger rodadasGlobais = new AtomicInteger(0);
    
    // número máximo de rodadas antes que o bar feche
    private final int MAX_RODADAS;
    
    // gerenciadores de threads
    private final ExecutorService poolClientes;
    private final ExecutorService poolGarcons;

    public AtendimentoBar(int totalClientes, int numGarcons, int capacidadeGarcons, int numRodadas) {
        this.MAX_RODADAS = numRodadas;
        this.poolClientes = Executors.newFixedThreadPool(totalClientes);
        this.poolGarcons = Executors.newFixedThreadPool(numGarcons);

        // inicia os garçons
        for (int i = 0; i < numGarcons; i++) {
            poolGarcons.submit(new Garcom(i, filaDePedidos, capacidadeGarcons, rodadasGlobais, MAX_RODADAS));
        }

        // inicia os clientes
        for (int i = 0; i < totalClientes; i++) {
            poolClientes.submit(new Cliente(i, filaDePedidos));
        }
    }

    // inicia a simulação 
    public void iniciarSimulacao() {
        // aguarda os garçons terminarem suas rodadas
        poolGarcons.shutdown(); // para de aceitar novas tarefas
        try {
            // aguarda até que todas as rodadas sejam concluídas
            if (!poolGarcons.awaitTermination(1, TimeUnit.HOURS)) {
                System.err.println("Tempo de simulação excedido.");
                poolGarcons.shutdownNow();
            }
        } catch (InterruptedException e) {
            System.err.println("Simulação (garçons) interrompida.");
            poolGarcons.shutdownNow();
        }

        // quando os garçons terminam, o bar fecha.
        System.out.println("\n--- O BAR FECHOU (Total de " + rodadasGlobais.get() + " rodadas servidas) ---");
        
        // interrompe todos os clientes
        poolClientes.shutdownNow();
        try {
            poolClientes.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            System.err.println("Simulação (clientes) interrompida.");
        }

        System.out.println("--- SIMULAÇÃO ENCERRADA ---");
    }

    public static void main(String[] args) {
        int totalClientes = 8;     // quantidade de clientes
        int numGarcons = 3;         // quantidade de garçons
        int capacidadeGarcons = 4;  // capacidade de cada garçom
        int numRodadas = 8;        // número total de rodadas a serem servidas

        if (args.length == 4) {
            try {
                totalClientes = Integer.parseInt(args[0]);
                numGarcons = Integer.parseInt(args[1]);
                capacidadeGarcons = Integer.parseInt(args[2]);
                numRodadas = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                System.err.println("Argumentos inválidos. Todos devem ser inteiros.");
                System.err.println("Uso: java AtendimentoBar [totalClientes numGarcons capacidadeGarcons numRodadas]");
                return;
            }
        } else if (args.length != 0) {
            System.out.println("Argumentos inválidos. Forneça 0 ou 4 argumentos.");
            System.out.println("Uso: java AtendimentoBar [totalClientes numGarcons capacidadeGarcons numRodadas]");
            return;
        }

        System.out.println(String.format(
            "Iniciando simulação: %d Clientes, %d Garçons (Capacidade %d), %d Rodadas totais.",
            totalClientes, numGarcons, capacidadeGarcons, numRodadas
        ));

        AtendimentoBar bar = new AtendimentoBar(totalClientes, numGarcons, capacidadeGarcons, numRodadas);
        bar.iniciarSimulacao();
    }
}
// representa um único pedido de um cliente
// contém um semáforo para que o cliente possa esperar e o garçom possa notificá-lo
class Pedido {
    private final int clienteId;
    // bloqueia o cliente até que o garçom dê release
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
            // loop de cliente: faz pedido, espera, come
            while (true) {
        
                Pedido meuPedido = new Pedido(id);
                System.out.println("Cliente " + id + " fez um pedido.");
                filaDePedidos.put(meuPedido); // coloca o pedido na fila

                System.out.println("Cliente " + id + " está esperando o pedido...");
                meuPedido.esperar(); // bloqueia até o garçom entregar

                System.out.println("Cliente " + id + " RECEBEU o pedido!");

                int tempoConsumo = rand.nextInt(3000) + 2000; // 2-5 segundos
                Thread.sleep(tempoConsumo);
                System.out.println("Cliente " + id + " terminou de consumir (levou " + tempoConsumo + "ms).");
                
                // pausa antes de fazer um novo pedido
                Thread.sleep(rand.nextInt(5000) + 1000); 
            }
        } catch (InterruptedException e) {
            // bar fechou thread interrompida
            System.out.println("Cliente " + id + " foi para casa (bar fechou).");
            Thread.currentThread().interrupt();
        }
    }
}


class Garcom implements Runnable {
    private final int id;
    private final BlockingQueue<Pedido> filaDePedidos;
    private final int capacidade;
    private final AtomicInteger rodadasGlobais;
    private final int MAX_RODADAS;

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
            // loop do garçom: continua trabalhando enquanto o bar não atingir número máximo de rodadas
            while (rodadasGlobais.get() < MAX_RODADAS) {
    
                List<Pedido> pedidosDaRodada = new ArrayList<>();
                
                System.out.println("Garçom " + id + " está esperando por pedidos.");
                Pedido primeiroPedido = filaDePedidos.take(); 
                pedidosDaRodada.add(primeiroPedido);

                // pega os pedidos restantes até a capacidade (C)
                filaDePedidos.drainTo(pedidosDaRodada, capacidade - 1);

                int numPedidos = pedidosDaRodada.size();
                System.out.println("Garçom " + id + " pegou " + numPedidos + " pedidos.");

                // registraPedidos()  (ida à copa)
                int tempoPreparo = 1000 + (numPedidos * 250); // Tempo baseado no nº de pedidos
                System.out.println("Garçom " + id + " está na copa (preparando " + numPedidos + " pedidos...)");
                Thread.sleep(tempoPreparo);

                System.out.println("Garçom " + id + " está entregando " + numPedidos + " pedidos:");
                for (Pedido pedido : pedidosDaRodada) {
                    System.out.println("\t-> Garçom " + id + " entregando ao Cliente " + pedido.getClienteId());
                    pedido.entregar(); // Libera o semáforo do cliente
                }

                int rodadaAtual = rodadasGlobais.incrementAndGet();
                System.out.println("Garçom " + id + " completou a rodada. (Rodada global: " + rodadaAtual + "/" + MAX_RODADAS + ")");

                Thread.sleep(500);
            }
        } catch (InterruptedException e) {
            // se o garçom for interrompido 
            System.out.println("Garçom " + id + " foi dispensado (interrompido).");
            Thread.currentThread().interrupt();
        }
        
        System.out.println("Garçom " + id + " terminou o expediente.");
    }
}
