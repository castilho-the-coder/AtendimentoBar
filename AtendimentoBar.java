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

    public AtendimentoBar(int totalClientes, int numGarcons, int capacidadeGarcons, int numRodadas, long timeoutMillis) {
        this.MAX_RODADAS = numRodadas;
        this.poolClientes = Executors.newFixedThreadPool(totalClientes);
        this.poolGarcons = Executors.newFixedThreadPool(numGarcons);

        // inicia os garçons
        // cria semáforos para forçar a ordem de início dos garçons
        Semaphore[] startsGarcons = new Semaphore[numGarcons];
        for (int i = 0; i < numGarcons; i++) {
            startsGarcons[i] = new Semaphore(0);
        }
        if (numGarcons > 0) startsGarcons[0].release();

        for (int i = 0; i < numGarcons; i++) {
            Semaphore nextG = (i + 1 < numGarcons) ? startsGarcons[i + 1] : null;
            poolGarcons.submit(new Garcom(i, filaDePedidos, capacidadeGarcons, rodadasGlobais, MAX_RODADAS, timeoutMillis, startsGarcons[i], nextG));
        }

        // inicia os clientes
        // cria semáforos para forçar a ordem de início dos clientes
        Semaphore[] starts = new Semaphore[totalClientes];
        for (int i = 0; i < totalClientes; i++) {
            starts[i] = new Semaphore(0);
        }
        // libera o primeiro cliente para iniciar
        if (totalClientes > 0) starts[0].release();

        for (int i = 0; i < totalClientes; i++) {
            Semaphore next = (i + 1 < totalClientes) ? starts[i + 1] : null;
            poolClientes.submit(new Cliente(i, filaDePedidos, starts[i], next));
        }
    }

    // inicia a simulação 
    public void iniciarSimulacao() {
        // aguarda os garçons terminarem suas rodadas
        poolGarcons.shutdown(); // para de aceitar novas tarefas
        try {
            // aguarda até que todas as rodadas sejam concluídas
                if (!poolGarcons.awaitTermination(1, TimeUnit.HOURS)) {
                System.out.println("Tempo de simulação excedido.");
                poolGarcons.shutdownNow();
            }
        } catch (InterruptedException e) {
            System.out.println("Simulação (garçons) interrompida.");
            poolGarcons.shutdownNow();
        }

        // quando os garçons terminam, o bar fecha.
        System.out.println("\n--- O BAR FECHOU (Total de " + rodadasGlobais.get() + " rodadas servidas) ---");
        
        // interrompe todos os clientes
        poolClientes.shutdownNow();
        try {
            poolClientes.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            System.out.println("Simulação (clientes) interrompida.");
        }

        System.out.println("--- SIMULAÇÃO ENCERRADA ---");
    }

    public static void main(String[] args) {
        int totalClientes = 5;     // quantidade de clientes
        int numGarcons = 3;         // quantidade de garçons
        int capacidadeGarcons = 3;  // capacidade de cada garçom
        int numRodadas = 6;        // número total de rodadas a serem servidas
        long timeoutMillis = 1000;  // timeout padrão (ms) para garçom esperar por pedidos extras

        if (args.length == 4 || args.length == 5) {
            try {
                totalClientes = Integer.parseInt(args[0]);
                numGarcons = Integer.parseInt(args[1]);
                capacidadeGarcons = Integer.parseInt(args[2]);
                numRodadas = Integer.parseInt(args[3]);
                if (args.length == 5) {
                    timeoutMillis = Long.parseLong(args[4]);
                }
            } catch (NumberFormatException e) {
                // argumentos inválidos (encerra o programa)
                return;
            }
        } else if (args.length != 0) {
            System.out.println("Argumentos inválidos. Forneça 0 ou 4 argumentos.");
            System.out.println("Uso: java AtendimentoBar [totalClientes numGarcons capacidadeGarcons numRodadas [timeoutMillis]]");
            return;
        }

        System.out.println(String.format(
            "Iniciando simulação: %d Clientes, %d Garçons (Capacidade %d), %d Rodadas totais.",
            totalClientes, numGarcons, capacidadeGarcons, numRodadas
        ));
        System.out.println("Timeout por pedido extra (ms): " + timeoutMillis);

        AtendimentoBar bar = new AtendimentoBar(totalClientes, numGarcons, capacidadeGarcons, numRodadas, timeoutMillis);
        bar.iniciarSimulacao();
    }
}

