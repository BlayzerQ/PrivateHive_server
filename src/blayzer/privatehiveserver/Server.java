package blayzer.privatehiveserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.util.*;

public class Server {

    String name;
    String key;
    String message;

    // Специальная "обёртка" для ArrayList, которая обеспечивает доступ к массиву из разных потоков
    private Map<String, Connection> connections =
            Collections.synchronizedMap(new HashMap<String, Connection>());
    private ServerSocket server;

    // Конструктор создаёт сервер-сокет, затем для каждого подключения создаётся объект Connection
    // и добавляет его в список подключений.
    public Server() {
        try {
            server = new ServerSocket(Main.Port);

            while (true) {
                Socket socket = server.accept();
                // Создаём объект Connection
                Connection connection = new Connection(socket);
                // Инициализирует поток и запускает метод run(),
                // которая выполняется одновременно с остальной программой
                connection.start();

            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }
    }

    // Закрывает все потоки, всех соединений, а также серверный сокет
    private void closeAll() {
        try {
            server.close();

            // Перебор всех Connection и вызов метода close() для каждого. Блок
            // synchronized {} необходим для правильного доступа к одним данным иp разных потоков
            synchronized(connections) {
                Iterator<Map.Entry<String, Connection>> iter = connections.entrySet().iterator();
                while(iter.hasNext()) {
                    ((Connection) iter.next()).close();
                }
            }
        } catch (Exception e) {
            System.err.println("Ошибка закрытия потоков!");
        }
    }

    // Класс содержит данные, относящиеся к конкретному подключению:
    // имя пользователя, сокет, входной поток BufferedReader, выходной поток PrintWriter
    // Расширяет Thread и в методе run() получает информацию от пользователя и пересылает её другим
    private class Connection extends Thread {
        private BufferedReader in;
        private PrintWriter out;
        private Socket socket;

        // Инициализирует поля объекта и получает имя пользователя
        // socket сокет, полученный из server.accept()
        public Connection(Socket socket) {
            this.socket = socket;

            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF8"));
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF8"), true);

                if(!in.readLine().isEmpty()){
                    name = in.readLine().trim();
                }
                if(!in.readLine().isEmpty()){
                    key = in.readLine().trim();
                }

                connections.put(name + "|" + key, this);

            } catch (IOException e) {
                e.printStackTrace();
                close();
            }
        }

        // Запрашивает имя пользователя, проверяет его и ожидает от него сообщений.
        // При получении сообщения, оно вместе с именем пользователя пересылается всем остальным.
        @Override
        public void run() {
            try {
                // Отправляем всем клиентам сообщение о том, что зашёл новый пользователь
                synchronized(connections) {
                    Iterator<Map.Entry<String, Connection>> iter = connections.entrySet().iterator();
                    while(iter.hasNext()) {
                        //Отправляем сообщение только тем клиентам, в идентификаторе которых есть ключ отправленных в сообщении
                        Map.Entry<String, Connection> pair = iter.next();
                        if(pair.getKey().split("\\|")[1].equals(key))
                            pair.getValue().out.println("[" + Utils.getTime() + "] " + name + " присоеденился");
                        System.out.println("[" + Utils.getTime() + "] " + name + " присоеденился");
                    }
                }

                while (true) {
                    String[] packet = in.readLine().split("\\|");
                    name = packet[0];
                    key = packet[1];
                    message = packet[2];

                    if(message.equals("debug")) System.out.println(name+": "+key+": "+message);
                    if(message.equals("exit")) break;

                    // Отправляем всем клиентам очередное сообщение, проверив его на пустые символы
                    synchronized(connections) {
                        if(!message.isEmpty() && !message.startsWith(" ") && !message.startsWith(" ")){
                            Iterator<Map.Entry<String, Connection>> iter = connections.entrySet().iterator();
                            while(iter.hasNext()) {
                                //Отправляем сообщение только тем клиентам, в идентификаторе которых есть ключ отправленных в сообщении
                                Map.Entry<String, Connection> pair = iter.next();
                                if(pair.getKey().split("\\|")[1].equals(key))
                                    pair.getValue().out.println("[" + Utils.getTime() + "] " + name + ": " + message);
                                System.out.println("[" + Utils.getTime() + "] " + name + ": " + message);
                            }
                        }
                    }
                }

                // Отправляем всем клиентам сообщение о том, что пользователь покинул чат
                synchronized(connections) {
                    Iterator<Map.Entry<String, Connection>> iter = connections.entrySet().iterator();
                    while(iter.hasNext()) {
                        //Отправляем сообщение только тем клиентам, в идентификаторе которых есть ключ отправленных в сообщении
                        Map.Entry<String, Connection> pair = iter.next();
                        if(pair.getKey().split("\\|")[1].equals(key))
                            pair.getValue().out.println("[" + Utils.getTime() + "] " + name + " покинул чат");
                        System.out.println("[" + Utils.getTime() + "] " + name + " покинул чат");
                    }
                }
            } catch (IOException e) {
                System.out.println("[" + Utils.getTime() + "] " + e.getMessage());
                System.out.println("[" + Utils.getTime() + "] " + name + " покинул чат");
            } finally {
                close();
            }
        }

//        public static String[] getMeta() {
//            String[] packet = new String[0];
//            try {
//                packet = in.readLine().split("\\|");
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//            return packet;
//        }

        // Закрывает входной, выходной потоки и сокет
        public void close() {
            try {
                in.close();
                out.close();
                socket.close();
                connections.remove(name);

            } catch (Exception e) {
                System.err.println("Ошибка закрытия потоков!");
            }
        }
    }
}
