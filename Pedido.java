import java.util.concurrent.Semaphore; // importa a classe Semaphore para sincronização entre cliente e garçom

public class Pedido { 
    private final int clienteId; // armazena o identificador do cliente que fez o pedido
    private final Semaphore semaforo = new Semaphore(0); // semáforo usado para bloquear/acionar o cliente

    public Pedido(int clienteId) { // construtor que registra o id do cliente
        this.clienteId = clienteId;
    }

    public int getClienteId() { // retorna o id do cliente responsável por este pedido
        return clienteId;
    }

    public Semaphore getSemaforo() { // expõe o semáforo para que o garçom possa liberar o cliente
        return semaforo;
    }

    public void esperar() throws InterruptedException { // método chamado pelo cliente para aguardar entrega
        semaforo.acquire(); // bloqueia até o garçom liberar este pedido
    }

    public void entregar() { // método chamado pelo garçom para sinalizar entrega
        semaforo.release(); // libera o semáforo, permitindo que o cliente prossiga
    }
}
