package sd;


import java.io.IOException;



import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import sd.model.Mensagem;

import com.google.gson.Gson;



public final class ProcessoBase extends Thread {
    

    protected boolean minerador;
    private final int id;   // random gera entre [100,999]
    public int numParticipantes = 4;
    MulticastSocket socket = null;
    InetAddress group;
    int porta = 6789;
    int portaUni = 7000;

    protected boolean jaExisteCliente = false;

    public ArrayList<Integer> idIntegrantes = new ArrayList<>();
    ArrayList<String> listaArquivos = new ArrayList<>();
    
    public int idMinerador; 

    Random r = new Random();
    public boolean jaRemoveu = false;
    Peer tCliente;
    Peer tMinerador;
    
    public ProcessoBase(String multiCastAdd) {
        id = r.nextInt(900) + 100;//id = [100,999]

        
        try {
            group = InetAddress.getByName(multiCastAdd);
            socket = new MulticastSocket(porta);
            socket.joinGroup(group);
            
        } catch (UnknownHostException ex) {
            System.out.println(ex.getMessage());
        } catch (IOException ex) {
            System.out.println(ex.getMessage());
        }
    }

    //adiciona id na arraylist
    private void adicionaId(int w) {
        if (idIntegrantes.contains(w) == false) {
            idIntegrantes.add(w);
        }
    }
    
    @Override
    public void run() {
        System.out.println("Novo Peer na area: "+id);
      

        //monta mensagem de novo membro
        Mensagem m = new Mensagem();
        m.tipo = m.NOVO_MEMBRO;
        m.mP = String.valueOf(id);;
        
        Gson gson = new Gson();
        String json = gson.toJson(m);
        System.out.println("Mensagem Enviada: "+json);
        byte msg[] = json.getBytes();

        DatagramPacket dp = new DatagramPacket(  msg ,  msg.length, group, porta);

        //paso 1. aguarda 4 processos entrarem na rede
        while (idIntegrantes.size() < numParticipantes) {
            //System.out.println(idIntegrantes.size());
            try {
                try {
                    Thread.currentThread().sleep(200);
                } catch (InterruptedException ex) {
                    Logger.getLogger(ProcessoBase.class.getName()).log(Level.SEVERE, null, ex);
                }
                socket.send(dp);
                byte[] buffer = new byte[1000];
                DatagramPacket messageIn = new DatagramPacket(buffer, buffer.length);


                socket.receive(messageIn); //se passar quer dizer que recebeu MSG
                
             
                String msgLidaString = new String(messageIn.getData());
                msgLidaString = msgLidaString.trim();
            //    System.out.println("Mensagem Recebida: "+msgLidaString);
                
                
                Mensagem mLida = new Mensagem();
                mLida = gson.fromJson(msgLidaString, Mensagem.class);

                // le a mensagem recebida:

          //      System.out.println("Mensagem recebida: "+mLida);
               if(mLida.tipo == Mensagem.NOVO_MEMBRO)
                adicionaId(Integer.parseInt(mLida.mP));
              
            } catch (IOException ex) {
                Logger.getLogger(ProcessoBase.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
      // 4 processos estao unidos
        try {

            socket.send(dp);
            System.out.println("enviou dp");
            try {
                Thread.currentThread().sleep(200);
            } catch (InterruptedException ex) {
                Logger.getLogger(ProcessoBase.class.getName()).log(Level.SEVERE, null, ex);
            }
            System.out.println(idIntegrantes.get(0));
            Collections.sort(idIntegrantes);
            
            this.idMinerador = Collections.max(idIntegrantes);
            this.idMinerador = elegeMinerador();
            
            System.out.println("Minerador Escolhido: "+idMinerador);
            
            
            
            
        } catch (IOException ex) {
            Logger.getLogger(ProcessoBase.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InterruptedException ex) {
            Logger.getLogger(ProcessoBase.class.getName()).log(Level.SEVERE, null, ex);
        }

       
    }

    public int getNumParticipantes() {
        return idIntegrantes.size();
    }

    public int elegeMinerador() throws SocketException, InterruptedException {
        //serÃ¡ eleito servidor aquele que possuir o maior ID
        int j;
        int maior = Collections.max(idIntegrantes);
        
        int portaCliente = portaUni +id;
        int portaServer = portaUni;

        if (id == maior) {
            
            minerador = true;
            System.out.println("Minerador Eleito!!!!!");
            System.out.println("sou Minerador Cliente: " + id);

        } else {
            System.out.println("sou peer normal:" + id);

        }
        

        if (jaExisteCliente == false) {
            tCliente = new Peer(socket, this, group, porta, id, portaCliente, portaServer,minerador);
            tCliente.start();
            jaExisteCliente = true;
            try {
                tCliente.join();
                
            } catch (InterruptedException ex) {
                Logger.getLogger(ProcessoBase.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
      
        return maior;

    }

    //porta = porta+1; 
    public int getIdProcesso() {
        return id;
    }

}
