import java.util.Random; 
import java.util.concurrent.BlockingQueue; // importa a interface de fila bloqueante para pedidos
import java.util.concurrent.Semaphore; 

public class Cliente implements Runnable { 
    private final int id; 
    private final BlockingQueue<Pedido> filaDePedidos; // referência à fila compartilhada de pedidos
    private final Random rand = new Random(); // gerador de números aleatórios para tempos
    private final Semaphore startSemaphore; // semáforo usado para controlar o início do cliente
    private final Semaphore nextSemaphore; // semáforo para liberar o próximo cliente na cadeia

    public Cliente(int id, BlockingQueue<Pedido> filaDePedidos, Semaphore startSemaphore, Semaphore nextSemaphore) {
        this.id = id; // salva o id 
        this.filaDePedidos = filaDePedidos; // salva referência à fila de pedidos
        this.startSemaphore = startSemaphore; // salva o semáforo de início
        this.nextSemaphore = nextSemaphore; // salva o semáforo do próximo cliente
    }

    @Override
    public void run() { 
        try {
        
            if (startSemaphore != null) {
                startSemaphore.acquire(); // aguarda permissão do semáforo antes de fazer o primeiro pedido
            }

            boolean firstTime = true; // flag para saber se é o primeiro pedido deste cliente
            while (true) { 
                Pedido meuPedido = new Pedido(id); // cria novo pedido associado a este cliente
                System.out.println("Cliente " + id + " fez um pedido."); // log de criação do pedido
                filaDePedidos.put(meuPedido); // coloca o pedido na fila 

                // após colocar o primeiro pedido libera o próximo cliente na cadeia
                if (firstTime) {
                    if (nextSemaphore != null) nextSemaphore.release(); // libera o próximo cliente
                    firstTime = false; // marca que já liberou o próximo
                }

                System.out.println("Cliente " + id + " está esperando o pedido..."); 
                meuPedido.esperar(); // bloqueia até que o garçom entregue (semaforo.release())

                System.out.println("Cliente " + id + " RECEBEU o pedido!"); // confirmação de recebimento

                int tempoConsumo = rand.nextInt(3000) + 2000; // calcula tempo de consumo aleatório (2-5s)
                Thread.sleep(tempoConsumo); // simula o tempo de consumo 
                System.out.println("Cliente " + id + " terminou de consumir."); 

                Thread.sleep(rand.nextInt(5000) + 1000); // espera aleatória antes de pedir novamente
            }
        } catch (InterruptedException e) { // captura interrupção (bar fechado)
            System.out.println("Cliente " + id + " foi para casa (bar fechou)."); // informa saída do cliente
            Thread.currentThread().interrupt(); 
        }
    }
}
