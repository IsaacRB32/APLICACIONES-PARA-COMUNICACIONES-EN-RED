/*
 * Servidor UDP para Practica 3
 * Maneja salas, usuarios y reenvío de mensajes
 */
package practica_3;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServidorChat {

    private static final int PUERTO = 8000;
    
    // Almacena: "NombreSala" -> Lista de Clientes (IP/Puerto)
    private static Map<String, List<Cliente>> salas = new HashMap<>();
    
    // Clase auxiliar para guardar quién es quién
    static class Cliente {
        InetAddress ip;
        int puerto;
        String nombre;

        public Cliente(InetAddress ip, int puerto, String nombre) {
            this.ip = ip;
            this.puerto = puerto;
            this.nombre = nombre;
        }
        
        // Para evitar duplicados en la lista (basado en IP y Puerto)
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Cliente c = (Cliente) o;
            return puerto == c.puerto && ip.equals(c.ip);
        }
    }

    public static void main(String[] args) {
        try {
            DatagramSocket socket = new DatagramSocket(PUERTO);
            System.out.println("✅ Servidor iniciado en puerto " + PUERTO);

            // Inicializamos una sala por defecto
            salas.put("General", new ArrayList<>());

            byte[] buffer = new byte[2048];

            while (true) {
                DatagramPacket paquete = new DatagramPacket(buffer, buffer.length);
                socket.receive(paquete);

                String mensaje = new String(paquete.getData(), 0, paquete.getLength());
                InetAddress ipCliente = paquete.getAddress();
                int puertoCliente = paquete.getPort();
                
                System.out.println("Recibido: " + mensaje + " de " + ipCliente + ":" + puertoCliente);

                // --- LÓGICA DE PROCESAMIENTO ---

                // 1. SOLICITUD DE SALAS (<getsalas/>)
                if (mensaje.contains("<getsalas/>")) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("<salas>");
                    for (String sala : salas.keySet()) {
                        sb.append("<sala>").append(sala).append("</sala>");
                    }
                    sb.append("</salas>");
                    
                    enviar(socket, sb.toString(), ipCliente, puertoCliente);
                }

                // 2. CREAR SALA (<crearsala>)
                else if (mensaje.contains("<crearsala>")) {
                    String nombreSala = extraerTag(mensaje, "nombre");
                    if (!salas.containsKey(nombreSala)) {
                        salas.put(nombreSala, new ArrayList<>());
                        System.out.println("Sala creada: " + nombreSala);
                    }
                }

                // 3. ENTRAR A SALA (<entrar>)
                else if (mensaje.contains("<entrar>")) {
                    String nombreUser = extraerTag(mensaje, "usr");
                    String nombreSala = extraerTag(mensaje, "sala");
                    
                    if (salas.containsKey(nombreSala)) {
                        Cliente nuevo = new Cliente(ipCliente, puertoCliente, nombreUser);
                        List<Cliente> usuariosEnSala = salas.get(nombreSala);
                        
                        // Evitamos duplicados si el usuario da clic varias veces
                        if (!usuariosEnSala.contains(nuevo)) {
                            usuariosEnSala.add(nuevo);
                        }
                        System.out.println(nombreUser + " entró a " + nombreSala);
                        // === NUEVO: ENVIAR LISTA ACTUALIZADA A TODOS ===
                        broadcastListaUsuarios(socket, nombreSala);
                    }
                }

                // 4. MENSAJE DE CHAT (<msg>) -> BROADCAST
                else if (mensaje.contains("<msg>")) {
                    String salaDestino = extraerTag(mensaje, "sala");
                    
                    if (salas.containsKey(salaDestino)) {
                        List<Cliente> usuarios = salas.get(salaDestino);
                        
                        // Reenviar a TODOS en esa sala
                        for (Cliente c : usuarios) {
                            enviar(socket, mensaje, c.ip, c.puerto);
                        }
                        System.out.println("Broadcast enviado a " + usuarios.size() + " usuarios en " + salaDestino);
                    }
                }
                
                // 5. INICIO (<inicio>) - Solo logueamos, no guardamos estado aún
                else if (mensaje.contains("<inicio>")) {
                    System.out.println("Cliente conectado: " + extraerTag(mensaje, "usr"));
                }
                // 6. SALIDA DE SALA (<leave>)
                else if (mensaje.contains("<leave>")) {
                    String nombreUser = extraerTag(mensaje, "usr");
                    String nombreSala = extraerTag(mensaje, "sala");

                    if (salas.containsKey(nombreSala)) {
                        List<Cliente> usuarios = salas.get(nombreSala);

                        // 1. Borrar al usuario de la lista
                        usuarios.removeIf(c -> c.nombre.equals(nombreUser));
                        System.out.println(nombreUser + " salió de " + nombreSala);

                        // 2. Actualizar la lista visual de usuarios (Ya lo tenías)
                        broadcastListaUsuarios(socket, nombreSala);

                        // --- NUEVO: AVISAR EN EL CHAT QUE SALIÓ ---
                        // Creamos un mensaje falso simulando ser el "Sistema" o "Servidor"
                        String mensajeSalida = "<msg>" +
                                               "<usr>SERVER</usr>" + 
                                               "<texto>" + nombreUser + " ha abandonado la sala.</texto>" +
                                               "<sala>" + nombreSala + "</sala>" +
                                               "</msg>";

                        // Reenviamos este aviso a todos los que quedan
                        for (Cliente c : usuarios) {
                            enviar(socket, mensajeSalida, c.ip, c.puerto);
                        }
                        // -------------------------------------------
                    }
                }
                // 7. MENSAJE PRIVADO (<privado>)
                else if (mensaje.contains("<privado>")) {
                    String emisor = extraerTag(mensaje, "usr");
                    String destinatario = extraerTag(mensaje, "dest");
                    String texto = extraerTag(mensaje, "texto");
                    String nombreSala = extraerTag(mensaje, "sala");

                    if (salas.containsKey(nombreSala)) {
                        List<Cliente> usuarios = salas.get(nombreSala);

                        // Buscamos al destinatario en la lista
                        for (Cliente c : usuarios) {
                            System.out.println("Comparando destinatario del XML: '" + destinatario + "' con usuario en lista: '" + c.nombre + "'"); // <--- AGREGA ESTO
                            if (c.nombre.equals(destinatario)) {
                                // ¡EUREKA! Encontramos a quien va dirigido. Enviamos solo a él.
                                enviar(socket, mensaje, c.ip, c.puerto);
                                System.out.println("Privado de " + emisor + " para " + destinatario);
                                break; // Ya lo encontramos, dejamos de buscar
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // Método para generar el XML de la lista y enviarlo a todos en la sala
private static void broadcastListaUsuarios(DatagramSocket socket, String nombreSala) {
    List<Cliente> usuarios = salas.get(nombreSala);
    
    // 1. Construir String: "Juan, Pedro, Maria"
    StringBuilder sbNombres = new StringBuilder();
    for (int i = 0; i < usuarios.size(); i++) {
        sbNombres.append(usuarios.get(i).nombre);
        if (i < usuarios.size() - 1) sbNombres.append(", ");
    }
    
    // 2. Construir XML: <lista><Sala>General</Sala><usrs>Juan, Pedro</usrs></lista>
    String xmlLista = "<lista><Sala>" + nombreSala + "</Sala><usrs>" + sbNombres.toString() + "</usrs></lista>";
    
    // 3. Enviar a todos los de esa sala
    for (Cliente c : usuarios) {
        enviar(socket, xmlLista, c.ip, c.puerto);
    }
}

    // Método para enviar mensajes
    private static void enviar(DatagramSocket s, String msg, InetAddress ip, int port) {
        try {
            byte[] data = msg.getBytes();
            DatagramPacket p = new DatagramPacket(data, data.length, ip, port);
            s.send(p);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // Método auxiliar para parsear XML simple
    private static String extraerTag(String xml, String tag) {
        try {
            String open = "<" + tag + ">";
            String close = "</" + tag + ">";
            int start = xml.indexOf(open);
            int end = xml.indexOf(close);
            
            if (start != -1 && end != -1) {
                return xml.substring(start + open.length(), end);
            }
        } catch (Exception e) {}
        return "";
    }
}

