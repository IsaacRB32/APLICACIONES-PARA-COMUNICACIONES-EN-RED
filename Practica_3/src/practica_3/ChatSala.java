/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JFrame.java to edit this template
 */
package practica_3;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import javax.swing.JOptionPane;

/**
 *
 * @author isaac
 */
public class ChatSala extends javax.swing.JFrame {
    private String usuario;
    private String sala;
    private DatagramSocket socket;
    // Modelo para manipular la lista visualmente
    private javax.swing.DefaultListModel<String> modeloUsuarios = new javax.swing.DefaultListModel<>();


    /**
     * Creates new form ChatSala
     */
    public ChatSala(String usuario, String sala, DatagramSocket socket) {
        initComponents();
        this.usuario = usuario;
        this.sala = sala;
        this.socket = socket;
        lstUsuarios.setModel(modeloUsuarios);

        setTitle("Sala: " + sala);

        // 1. PRIMERO: Empezamos a escuchar
        iniciarListener(); 

        // 2. SEGUNDO: Configuramos el listener de cierre (lo que ya tenías)
        this.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                 // ... tu código de <leave> ...
                 // (Puedes usar enviarPaquete("<leave>...") aquí también si quieres limpiar código)
                 try {
                    String salidaXML = "<leave><usr>" + usuario + "</usr><sala>" + sala + "</sala></leave>";
                    enviarPaquete(salidaXML); // Usando el nuevo método
                 } catch (Exception ex) {}
            }
        });

        // 3. TERCERO (EL ARREGLO): ¡Ahora sí avisamos que entramos!
        // Como el listener ya está corriendo arriba, capturaremos la respuesta de la lista.
        String entrarXML = "<entrar><usr>" + usuario + "</usr><sala>" + sala + "</sala></entrar>";
        enviarPaquete(entrarXML);
    }
    
    // ==========================
    // HILO PARA RECIBIR MENSAJES
    // ==========================
    private void iniciarListener() {
        Thread hilo = new Thread(() -> {
            while (true) {
                try {
                    byte[] buffer = new byte[2048];
                    DatagramPacket paquete = new DatagramPacket(buffer, buffer.length);
                    socket.receive(paquete);

                    String msg = new String(paquete.getData(), 0, paquete.getLength());

                    // CASO 1: Mensaje normal (Ya lo tienes)
                    if (msg.contains("<sala>" + sala + "</sala>") && msg.contains("<texto>")) { 
                        agregarMensaje(msg);
                    }

                    // CASO 2: Actualización de Lista de Usuarios (NUEVO)
                    else if (msg.contains("<lista>") && msg.contains("<Sala>" + sala + "</Sala>")) {
                        actualizarListaUsuarios(msg);
                    }

                } catch (Exception e) {
                    System.out.println("Error recibiendo: " + e.getMessage());
                }
            }
        });

        hilo.setDaemon(true);
        hilo.start();
    }
    
    
    
    //actualizarListaUsuarios
    private void actualizarListaUsuarios(String xml) {
    try {
        // Formato esperado: <lista><Sala>XD</Sala><usrs>Isaac, Carlos, Pedro</usrs></lista>
        int iUsrs = xml.indexOf("<usrs>");
        int fUsrs = xml.indexOf("</usrs>");
        
        // Extraer "Isaac, Carlos, Pedro"
        String listaStr = xml.substring(iUsrs + 6, fUsrs);
        
        // Separar por comas
        String[] usuarios = listaStr.split(",");
        
        // Actualizar la GUI de forma segura
        javax.swing.SwingUtilities.invokeLater(() -> {
            modeloUsuarios.clear();
            for (String u : usuarios) {
                modeloUsuarios.addElement(u.trim());
            }
        });
    } catch (Exception e) {
        System.out.println("Error parseando lista: " + e.getMessage());
    }
}
    
        // ==========================
    // IMPRIMIR MENSAJE EN EL CHAT
    // ==========================
    private void agregarMensaje(String xml) {
        try {
            // Estructura esperada:
            // <msg><usr>Isaac</usr><texto>Hola</texto><sala>General</sala></msg>

            int iUser = xml.indexOf("<usr>");
            int fUser = xml.indexOf("</usr>");
            String usr = xml.substring(iUser + 5, fUser);

            int iTxt = xml.indexOf("<texto>");
            int fTxt = xml.indexOf("</texto>");
            String texto = xml.substring(iTxt + 7, fTxt);

            javax.swing.SwingUtilities.invokeLater(() -> {
                txtChat.append(usr + ": " + texto + "\n");
            });

        } catch (Exception e) {
            txtChat.append("[Mensaje malformado]\n");
        }
    }


     // ==========================
    // ENVIAR MENSAJE AL SERVIDOR
    // ==========================
    private void enviarMensaje() {
        try {
            String texto = txtMensaje.getText().trim();
            if (texto.isEmpty()) return;

            String xml = "<msg><usr>" + usuario + "</usr><texto>"
                    + texto + "</texto><sala>" + sala + "</sala></msg>";

            byte[] buffer = xml.getBytes();
            DatagramPacket paquete = new DatagramPacket(
                buffer, buffer.length,
                InetAddress.getByName("127.0.0.1"),
                8000 // <--- CAMBIO AQUÍ
            );

            socket.send(paquete);
            txtMensaje.setText("");

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error enviando: " + e.getMessage());
        }
    }
    
    // Método auxiliar para enviar XMLs de sistema (como entrar o salir)
    private void enviarPaquete(String xml) {
        try {
            byte[] buffer = xml.getBytes();
            DatagramPacket paquete = new DatagramPacket(
                buffer, buffer.length,
                InetAddress.getByName("127.0.0.1"),
                8000
            );
            socket.send(paquete);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        txtChat = new javax.swing.JTextArea();
        txtMensaje = new javax.swing.JTextField();
        btnEnviar = new javax.swing.JButton();
        jScrollPane2 = new javax.swing.JScrollPane();
        lstUsuarios = new javax.swing.JList<>();
        jLabel1 = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        txtChat.setEditable(false);
        txtChat.setColumns(20);
        txtChat.setRows(5);
        jScrollPane1.setViewportView(txtChat);

        txtMensaje.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                txtMensajeActionPerformed(evt);
            }
        });

        btnEnviar.setText("Enviar");
        btnEnviar.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnEnviarActionPerformed(evt);
            }
        });

        jScrollPane2.setViewportView(lstUsuarios);

        jLabel1.setText("Usuarios");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(19, 19, 19)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(txtMensaje, javax.swing.GroupLayout.PREFERRED_SIZE, 289, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnEnviar))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 108, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(layout.createSequentialGroup()
                                .addGap(32, 32, 32)
                                .addComponent(jLabel1)))
                        .addGap(18, 18, 18)
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 252, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(17, 17, 17)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 217, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 194, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtMensaje, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnEnviar))
                .addContainerGap(25, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void txtMensajeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_txtMensajeActionPerformed
        // TODO add your handling code here:
        enviarMensaje();
    }//GEN-LAST:event_txtMensajeActionPerformed

    private void btnEnviarActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnEnviarActionPerformed
        // TODO add your handling code here:
        enviarMensaje();
    }//GEN-LAST:event_btnEnviarActionPerformed

    /**
     * @param args the command line arguments
     */

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnEnviar;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JList<String> lstUsuarios;
    private javax.swing.JTextArea txtChat;
    private javax.swing.JTextField txtMensaje;
    // End of variables declaration//GEN-END:variables
}
