package blayzer.privatehiveserver;

public class Main {
    public static final int Port = 8283; // Порт на котором работает чат
    public static void main(String[] args) {
        System.out.println("PrivateHive Server успешно запущен");

        new Server();
    }
}
