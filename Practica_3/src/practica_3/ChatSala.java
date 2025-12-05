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
    // Variable para almacenar los pedacitos de archivos que van llegando
    // Mapa: ID_ARCHIVO -> (Numero_Secuencia -> Datos_Bytes)
    private java.util.Map<String, java.util.Map<Integer, byte[]>> bufferRecepcion = new java.util.HashMap<>();
    
    private javax.sound.sampled.TargetDataLine microphone;
    private boolean isRecording = false;
    private java.io.File audioFileTemp;


    /**
     * Creates new form ChatSala
     */
    public ChatSala(String usuario, String sala, DatagramSocket socket) {
        initComponents();
        this.usuario = usuario;
        this.sala = sala;
        this.socket = socket;
        lstUsuarios.setModel(modeloUsuarios);

        //setTitle("Sala: " + sala);
        setTitle("Sala: " + sala + "  |  Usuario: " + usuario);

        // 1. PRIMERO: Empezamos a escuchar
        iniciarListener(); 

        // 2. SEGUNDO: Configuramos el listener de cierre (lo que ya tenías)
        this.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                 try {
                    String salidaXML = "<leave><usr>" + usuario + "</usr><sala>" + sala + "</sala></leave>";
                    enviarPaquete(salidaXML); // Usando el nuevo método
                 } catch (Exception ex) {}
            }
        });
        // ========================================================
        // CONFIGURACIÓN DEL MENÚ DE MENSAJES PRIVADOS (CLIC DERECHO)
        // ========================================================
        javax.swing.JPopupMenu menuUsuarios = new javax.swing.JPopupMenu();
        javax.swing.JMenuItem itemPrivado = new javax.swing.JMenuItem("Enviar Mensaje Privado");
        menuUsuarios.add(itemPrivado);

        itemPrivado.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                // Obtenemos a quién le diste clic
                String destinatario = lstUsuarios.getSelectedValue();

                // Validaciones
                if (destinatario == null) {
                    javax.swing.JOptionPane.showMessageDialog(ChatSala.this, "Selecciona un usuario.");
                    return;
                }
                if (destinatario.equals(usuario)) {
                    javax.swing.JOptionPane.showMessageDialog(ChatSala.this, "No te escribas a ti mismo.");
                    return;
                }

                // Pedimos el texto
                String texto = javax.swing.JOptionPane.showInputDialog(ChatSala.this, "Privado para " + destinatario + ":");
                if (texto != null && !texto.trim().isEmpty()) {

                    // 1. Mostrarlo en MI propia pantalla (para saber qué envié)
                    txtChat.append("[Privado a " + destinatario + "]: " + texto + "\n");

                    // 2. Construir XML y Enviar al servidor
                    String xmlPrivado = "<privado>" +
                                        "<sala>" + sala + "</sala>" +
                                        "<usr>" + usuario + "</usr>" +
                                        "<dest>" + destinatario + "</dest>" +
                                        "<texto>" + texto + "</texto>" +
                                        "</privado>";
                    enviarPaquete(xmlPrivado);
                }
            }
        });

        // Vinculamos el menú a la lista visual
        lstUsuarios.setComponentPopupMenu(menuUsuarios);
        // ========================================================
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
                    byte[] buffer = new byte[4096];
                    DatagramPacket paquete = new DatagramPacket(buffer, buffer.length);
                    socket.receive(paquete);

                    String msg = new String(paquete.getData(), 0, paquete.getLength());

                    // CASO 1: Mensaje normal (AGREGAMOS "msg.contains(<msg>)")
                    if (msg.contains("<msg>") && msg.contains("<sala>" + sala + "</sala>") && msg.contains("<texto>")) { 
                        agregarMensaje(msg);
                    }

                    // CASO 2: Lista
                    else if (msg.contains("<lista>") && msg.contains("<Sala>" + sala + "</Sala>")) {
                        actualizarListaUsuarios(msg);
                    }

                    // CASO 3: Mensaje Privado
                    else if (msg.contains("<privado>")) {
                        try {
                            int iUsr = msg.indexOf("<usr>");
                            int fUsr = msg.indexOf("</usr>");
                            String emisor = msg.substring(iUsr + 5, fUsr);

                            int iTxt = msg.indexOf("<texto>");
                            int fTxt = msg.indexOf("</texto>");
                            String texto = msg.substring(iTxt + 7, fTxt);

                            String linea = ">>> [Privado de " + emisor + "]: " + texto + "\n";

                            javax.swing.SwingUtilities.invokeLater(() -> {
                                txtChat.append(linea);
                            });
                        } catch (Exception e) {}
                    }
                    // CASO 4: FRAGMENTOS (Recepción de archivos)
                    else if (msg.contains("<fragment>")) {
                        procesarFragmento(msg);
                    }
                } catch (Exception e) {
                    System.out.println("Error recibiendo: " + e.getMessage());
                }
            }
        });

        hilo.setDaemon(true);
        hilo.start();
    }
    
    
    // ==========================================
    // LÓGICA DE REENSAMBLAJE (IMAGEN Y AUDIO)
    // ==========================================
    private void procesarFragmento(String xml) {
        try {
            String fid = extraerValor(xml, "fid");
            String usr = extraerValor(xml, "usr");
            // 1. LEER EL TIPO (audio o image)
            String tipo = extraerValor(xml, "type"); 
            
            int seq = Integer.parseInt(extraerValor(xml, "seq"));
            int total = Integer.parseInt(extraerValor(xml, "total"));
            String dataBase64 = extraerValor(xml, "data");
            
            if (!bufferRecepcion.containsKey(fid)) {
                bufferRecepcion.put(fid, new java.util.HashMap<>());
            }
            
            byte[] chunkBytes = java.util.Base64.getDecoder().decode(dataBase64);
            bufferRecepcion.get(fid).put(seq, chunkBytes);
            
            if (bufferRecepcion.get(fid).size() == total) {
                System.out.println("Archivo completado. Tipo: " + tipo);
                // 2. LLAMAR AL RECONSTRUCTOR CON EL TIPO
                reconstruirArchivo(fid, usr, total, tipo);
            }
            
        } catch (Exception e) {
            System.out.println("Error procesando fragmento: " + e.getMessage());
        }
    }

    // Este método decide qué hacer con los bytes completos
        private void reconstruirArchivo(String fid, String autor, int totalParts, String tipo) {
            try {
                java.util.Map<Integer, byte[]> partes = bufferRecepcion.get(fid);
                int totalSize = 0;
                for (byte[] b : partes.values()) totalSize += b.length;

                byte[] archivoCompleto = new byte[totalSize];
                int currentPos = 0;
                for (int i = 1; i <= totalParts; i++) {
                    if (partes.containsKey(i)) {
                        byte[] pedazo = partes.get(i);
                        System.arraycopy(pedazo, 0, archivoCompleto, currentPos, pedazo.length);
                        currentPos += pedazo.length;
                    } else {
                        return; 
                    }
                }

                // 3. DECISIÓN: ¿ES AUDIO O FOTO?
                if (tipo != null && tipo.equals("audio")) {
                    reproducirAudio(archivoCompleto, autor);
                } else {
                    mostrarImagen(archivoCompleto, autor);
                }

                bufferRecepcion.remove(fid);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // --- SUB-MÉTODO PARA MOSTRAR FOTOS ---
        private void mostrarImagen(byte[] bytes, String autor) {
            try {
                javax.swing.ImageIcon icono = new javax.swing.ImageIcon(bytes);

                // Validación rápida
                if (icono.getIconWidth() <= 0) return; 

                java.awt.Image imgEscalada = icono.getImage().getScaledInstance(400, -1, java.awt.Image.SCALE_SMOOTH);
                javax.swing.ImageIcon iconoFinal = new javax.swing.ImageIcon(imgEscalada);

                javax.swing.SwingUtilities.invokeLater(() -> {
                    if (autor.equals(this.usuario)) {
                        txtChat.append("Tú has enviado un Sticker\n");
                        JOptionPane.showMessageDialog(null, "Sticker enviado.", "Enviado", JOptionPane.PLAIN_MESSAGE, iconoFinal);
                    } else {
                        txtChat.append(autor + " ha enviado un Sticker\n");
                        JOptionPane.showMessageDialog(null, "De: " + autor, "Sticker Recibido", JOptionPane.PLAIN_MESSAGE, iconoFinal);
                    }
                });
            } catch (Exception e) {}
        }

        // --- SUB-MÉTODO PARA REPRODUCIR AUDIO ---
        private void reproducirAudio(byte[] bytes, String autor) {
            try {
                // Convertir bytes en stream de audio
                java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(bytes);
                javax.sound.sampled.AudioInputStream audioStream = javax.sound.sampled.AudioSystem.getAudioInputStream(bis);
                javax.sound.sampled.Clip clip = javax.sound.sampled.AudioSystem.getClip();
                clip.open(audioStream);

                javax.swing.SwingUtilities.invokeLater(() -> {
                    if (autor.equals(this.usuario)) {
                        txtChat.append("Tú has enviado un Audio\n");
                    } else {
                        txtChat.append(autor + " ha enviado un Audio\n");

                        // Preguntar si quiere escuchar
                        int resp = JOptionPane.showConfirmDialog(null, 
                                "Nota de voz de " + autor + ". ¿Escuchar?", 
                                "Audio Recibido", 
                                JOptionPane.YES_NO_OPTION);

                        if (resp == JOptionPane.YES_OPTION) {
                            clip.start();
                        }
                    }
                });
            } catch (Exception e) {
                System.out.println("Error audio: " + e.getMessage());
                e.printStackTrace(); // Ver si hay error de formato
            }
        }

        private String extraerValor(String xml, String tag) {
            int start = xml.indexOf("<" + tag + ">");
            int end = xml.indexOf("</" + tag + ">");
            if (start == -1 || end == -1) return "";
            return xml.substring(start + tag.length() + 2, end);
        }

    private void reconstruirYMostrarImagen(String fid, String autor, int totalParts) {
        try {
            // Calcular tamaño total
            java.util.Map<Integer, byte[]> partes = bufferRecepcion.get(fid);
            int totalSize = 0;
            for (byte[] b : partes.values()) totalSize += b.length;

            // --- DIAGNÓSTICO ---
            System.out.println("DEBUG: Reconstruyendo sticker de " + totalSize + " bytes.");
            // -------------------

            // Pegar todos los bytes
            byte[] imagenCompleta = new byte[totalSize];

            int currentPos = 0;
            for (int i = 1; i <= totalParts; i++) {
                if (partes.containsKey(i)) {
                    byte[] pedazo = partes.get(i);
                    System.arraycopy(pedazo, 0, imagenCompleta, currentPos, pedazo.length);
                    currentPos += pedazo.length;
                } else {
                    System.out.println("Falta el paquete " + i);
                    return;
                }
            }
            // === DIAGNÓSTICO DE CABECERA (AGREGA ESTO) ===
            System.out.print("HEX DUMP (Primeros 4 bytes): ");
            if (imagenCompleta.length >= 4) {
                for(int k=0; k<4; k++) {
                    System.out.printf("%02X ", imagenCompleta[k]);
                }
            }
            System.out.println();

            // Crear imagen
            javax.swing.ImageIcon icono = new javax.swing.ImageIcon(imagenCompleta);

            // --- DIAGNÓSTICO ---
            if (icono.getIconWidth() <= 0) {
                System.out.println("ERROR: La imagen se creó pero tiene ancho 0. ¿Es un formato válido (.png, .jpg)?");
            } else {
                System.out.println("ÉXITO: Imagen creada. Dimensiones: " + icono.getIconWidth() + "x" + icono.getIconHeight());
            }
            // -------------------

            // Escalar
            java.awt.Image imgEscalada = icono.getImage().getScaledInstance(400, -1, java.awt.Image.SCALE_SMOOTH);
            javax.swing.ImageIcon iconoFinal = new javax.swing.ImageIcon(imgEscalada);

            // Mostrar
            javax.swing.SwingUtilities.invokeLater(() -> {
                if (autor.equals(this.usuario)) {
                    // CASO A: SOY YO (El remitente)
                    txtChat.append("Tú has enviado un Sticker\n");

                    // Opcional: Si no quieres que te salte la ventana a ti mismo, comenta la línea de abajo.
                    // O cámbiale el título para que tenga sentido:
                    JOptionPane.showMessageDialog(null, 
                        "Tu sticker se envió correctamente a la sala.", 
                        "Sticker Enviado", 
                        JOptionPane.PLAIN_MESSAGE, 
                        iconoFinal);

                } else {
                    // CASO B: ES OTRO (El destinatario)
                    txtChat.append(autor + " ha enviado un Sticker\n");

                    JOptionPane.showMessageDialog(null, 
                        "Imagen recibida de: " + autor, 
                        "Sticker Recibido", 
                        JOptionPane.PLAIN_MESSAGE, 
                        iconoFinal);
                }
            });

            bufferRecepcion.remove(fid);

        } catch (Exception e) {
            e.printStackTrace();
        }
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
                8000
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
    
    // Método auxiliar para enviar un archivo de audio (sea elegido o grabado)
    private void enviarArchivoAudio(java.io.File archivo) {
        new Thread(() -> {
            try {
                // Validación de tamaño (2MB máx para audio)
                if (archivo.length() > 2000000) { 
                    javax.swing.SwingUtilities.invokeLater(() -> 
                        JOptionPane.showMessageDialog(this, "Audio muy grande.")
                    );
                    return;
                }

                byte[] fileBytes = java.nio.file.Files.readAllBytes(archivo.toPath());

                // Generar ID único
                String fileID = usuario + "-" + System.currentTimeMillis();

                int chunkSize = 2048; 
                int totalParts = (int) Math.ceil((double) fileBytes.length / chunkSize);

                javax.swing.SwingUtilities.invokeLater(() -> 
                    txtChat.append(">> Enviando nota de voz...\n")
                );

                for (int i = 0; i < totalParts; i++) {
                    int start = i * chunkSize;
                    int length = Math.min(fileBytes.length - start, chunkSize);
                    byte[] chunk = new byte[length];
                    System.arraycopy(fileBytes, start, chunk, 0, length);

                    String chunkBase64 = java.util.Base64.getEncoder().encodeToString(chunk);

                    // XML con type=audio
                    String xmlFrag = "<fragment>" +
                                     "<sala>" + sala + "</sala>" +
                                     "<usr>" + usuario + "</usr>" +
                                     "<fid>" + fileID + "</fid>" +
                                     "<seq>" + (i + 1) + "</seq>" +
                                     "<total>" + totalParts + "</total>" +
                                     "<type>audio</type>" +
                                     "<data>" + chunkBase64 + "</data>" +
                                     "</fragment>";

                    enviarPaquete(xmlFrag);

                    // Pausa técnica para no saturar
                    Thread.sleep(5); 
                }

                // Si fue un archivo temporal grabado, lo borramos para limpiar
                if (archivo.getName().startsWith("nota_voz_")) {
                    archivo.delete();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
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
        btnSalir = new javax.swing.JButton();
        btnSticker = new javax.swing.JButton();
        btnGrabar = new javax.swing.JButton();

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

        btnSalir.setText("Abandonar");
        btnSalir.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSalirActionPerformed(evt);
            }
        });

        btnSticker.setText("Sticker");
        btnSticker.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnStickerActionPerformed(evt);
            }
        });

        btnGrabar.setText("Audio");
        btnGrabar.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnGrabarActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(19, 19, 19)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 108, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(layout.createSequentialGroup()
                                .addGap(32, 32, 32)
                                .addComponent(jLabel1))
                            .addGroup(layout.createSequentialGroup()
                                .addGap(16, 16, 16)
                                .addComponent(btnSalir)))
                        .addGap(18, 18, 18)
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 353, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(txtMensaje, javax.swing.GroupLayout.PREFERRED_SIZE, 254, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnEnviar)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnSticker)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnGrabar)))
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
                        .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 167, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(btnSalir)))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtMensaje, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnEnviar)
                    .addComponent(btnSticker)
                    .addComponent(btnGrabar))
                .addContainerGap(18, Short.MAX_VALUE))
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

    private void btnSalirActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSalirActionPerformed
        // TODO add your handling code here:
        try {
            // 1. Enviar aviso al servidor (<leave>)
            String salidaXML = "<leave><usr>" + usuario + "</usr><sala>" + sala + "</sala></leave>";
            enviarPaquete(salidaXML);

            // 2. Cerrar el socket actual (Importante para liberar el puerto)
            socket.close();

            // 3. Regresar a la ventana de selección de salas
            // Creamos un socket nuevo y limpio para el selector
            DatagramSocket nuevoSocket = new DatagramSocket();
            SalaSelector selector = new SalaSelector(usuario, nuevoSocket);
            selector.setVisible(true);

            // 4. Cerrar esta ventana de chat
            this.dispose();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }//GEN-LAST:event_btnSalirActionPerformed

    private void btnStickerActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnStickerActionPerformed
        // TODO add your handling code here:
        // 1. Selector de archivos
        javax.swing.JFileChooser fileChooser = new javax.swing.JFileChooser();
        int seleccion = fileChooser.showOpenDialog(this);

        if (seleccion == javax.swing.JFileChooser.APPROVE_OPTION) {
            // Usamos un Hilo nuevo para no congelar la ventana mientras envía los 500 paquetes
            new Thread(() -> {
                try {
                    java.io.File archivo = fileChooser.getSelectedFile();
                    byte[] fileBytes = java.nio.file.Files.readAllBytes(archivo.toPath());

                    // Generar ID único para este archivo (Usuario + Tiempo)
                    String fileID = usuario + "-" + System.currentTimeMillis();

                    // Tamaño del pedazo (Chunk). 2048 bytes es seguro y eficiente.
                    int chunkSize = 2048; 
                    int totalParts = (int) Math.ceil((double) fileBytes.length / chunkSize);

                    // Avisar en mi chat
                    javax.swing.SwingUtilities.invokeLater(() -> 
                        txtChat.append(">> Enviando sticker ...\n")
                    );

                    for (int i = 0; i < totalParts; i++) {
                        // a) Cortar el pedazo correspondiente
                        int start = i * chunkSize;
                        int length = Math.min(fileBytes.length - start, chunkSize);
                        byte[] chunk = new byte[length];
                        System.arraycopy(fileBytes, start, chunk, 0, length);

                        // b) Convertir a Base64
                        String chunkBase64 = java.util.Base64.getEncoder().encodeToString(chunk);

                        // c) Armar el XML del fragmento
                        // <fragment><sala>...</sala><usr>...</usr><fid>ID</fid><seq>1</seq><total>10</total><data>...</data></fragment>
                        String xmlFrag = "<fragment>" +
                                         "<sala>" + sala + "</sala>" +
                                         "<usr>" + usuario + "</usr>" +
                                         "<fid>" + fileID + "</fid>" +
                                         "<seq>" + (i + 1) + "</seq>" +
                                         "<total>" + totalParts + "</total>" +
                                         "<data>" + chunkBase64 + "</data>" +
                                         "</fragment>";

                        // d) Enviar
                        enviarPaquete(xmlFrag);

                        // IMPORTANTE: Pausa milimétrica para no ahogar la red
                        Thread.sleep(5); 
                    }

                    javax.swing.SwingUtilities.invokeLater(() -> 
                        txtChat.append(">> Sticker enviado correctamente.\n")
                    );

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }//GEN-LAST:event_btnStickerActionPerformed

    private void btnGrabarActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnGrabarActionPerformed
        // TODO add your handling code here:
            if (!isRecording) {
            // --- INICIAR GRABACIÓN ---
            try {
                // 1. Configurar formato de audio (Calidad de voz estándar: 16kHz, 16bit, Mono)
                javax.sound.sampled.AudioFormat format = new javax.sound.sampled.AudioFormat(
                    16000, 16, 1, true, true);

                // 2. Obtener micrófono
                javax.sound.sampled.DataLine.Info info = new javax.sound.sampled.DataLine.Info(
                    javax.sound.sampled.TargetDataLine.class, format);

                if (!javax.sound.sampled.AudioSystem.isLineSupported(info)) {
                    JOptionPane.showMessageDialog(this, "Micrófono no detectado.");
                    return;
                }

                microphone = (javax.sound.sampled.TargetDataLine) javax.sound.sampled.AudioSystem.getLine(info);
                microphone.open(format);
                microphone.start();

                // 3. Hilo para guardar lo que escucha el micrófono en un archivo .wav temporal
                Thread recordingThread = new Thread(() -> {
                    try {
                        audioFileTemp = new java.io.File("nota_voz_" + System.currentTimeMillis() + ".wav");
                        javax.sound.sampled.AudioInputStream ais = new javax.sound.sampled.AudioInputStream(microphone);
                        // Esto bloquea el hilo grabando hasta que cerremos el micrófono
                        javax.sound.sampled.AudioSystem.write(ais, javax.sound.sampled.AudioFileFormat.Type.WAVE, audioFileTemp);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });

                recordingThread.start();
                isRecording = true;
                btnGrabar.setText("Parar");
                txtChat.append(">> Grabando audio...️\n");

            } catch (Exception e) {
                e.printStackTrace();
            }

        } else {
            // --- DETENER Y ENVIAR ---
            try {
                // 1. Detener micrófono (esto libera el hilo de grabación)
                if (microphone != null) {
                    microphone.stop();
                    microphone.close();
                }
                isRecording = false;
                btnGrabar.setText("Grabar");

                // 2. Esperar un poquito a que el archivo se guarde bien en disco
                Thread.sleep(500); 

                // 3. ¡ENVIAR EL ARCHIVO!
                if (audioFileTemp != null && audioFileTemp.exists()) {
                    enviarArchivoAudio(audioFileTemp);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }//GEN-LAST:event_btnGrabarActionPerformed

    /**
     * @param args the command line arguments
     */

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnEnviar;
    private javax.swing.JButton btnGrabar;
    private javax.swing.JButton btnSalir;
    private javax.swing.JButton btnSticker;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JList<String> lstUsuarios;
    private javax.swing.JTextArea txtChat;
    private javax.swing.JTextField txtMensaje;
    // End of variables declaration//GEN-END:variables
}
