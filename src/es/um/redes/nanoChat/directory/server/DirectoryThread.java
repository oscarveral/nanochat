package es.um.redes.nanoChat.directory.server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import es.um.redes.nanoChat.directory.protocol.DirectoryMessage;

/**
 * Clase que representa un hilo de ejecución del directorio en el servidor.
 */
public class DirectoryThread extends Thread 
{
	/* Estructura para guardar las asociaciones ID_PROTOCOLO -> Dirección del servidor. */
	protected Map<Integer,InetSocketAddress> servers;
	/* Socket de comunicación UDP. */
	protected DatagramSocket socket = null;
	/* Probabilidad de descarte del mensaje. */
	protected double messageDiscardProbability;

	/* Constructor del hilo del servidor. */
	public DirectoryThread(String name, int directoryPort, double corruptionProbability) throws SocketException 
	{
		super(name);
		
		/* Creamos la dirección en la que escucha el servidor de directorio. */
		InetSocketAddress localAddr = new InetSocketAddress(directoryPort);		
		/* Creación del socket del servidor. */
		this.socket = new DatagramSocket(localAddr);
		
		this.messageDiscardProbability = corruptionProbability;
		
		/* Inicialización del mapa.*/
		servers = new HashMap<Integer,InetSocketAddress>();
	}

	@Override
	public void run() 
	{
		/* Buffer donde almacenaremos los datos de las solicitudes entrantes. */
		byte[] buf = new byte[DirectoryMessage.PACKET_MAX_SIZE];

		System.out.println("Directory starting...");
		
		boolean running = true;
		while (running) 
		{
			/* 1) Recibimos el paquete de solicitud. Si se produce un error se muestra y se continua intentando la recepción de una nueva solicitud. */
			DatagramPacket pckt = new DatagramPacket(buf, buf.length);
			try 
			{
				this.socket.receive(pckt);
			} 
			catch (IOException e) 
			{
				System.err.println("Error receiving a package. Ignoring...\n");
				continue;
			}
						
			/* 2) Extraemos la dirección del cliente que realiza la solicitud. */ 
			InetSocketAddress clientAddr = (InetSocketAddress) pckt.getSocketAddress();
			
			/* Mensaje informativo. */
			System.out.println("Packet recieved from client " + clientAddr.toString() + ".");
						
		
			/* 3) Vemos si el mensaje debe ser descartado por la probabilidad de descarte. */
			double rand = Math.random();
			if (rand < messageDiscardProbability) 
			{
				System.err.println("Directory DISCARDED corrupt request from: " + clientAddr.toString() + "\n");
				continue;
			}

			/* 4) Analizar y procesar la solicitud (llamada a processRequestFromCLient). Si algo sale mal se pondrá por consola. */
			try 
			{
				this.processRequestFromClient(pckt.getData(), clientAddr);
			} 
			catch (IOException e) 
			{
				System.err.println("Can't process the message. " + e.getMessage() + " Ignoring...\n");
				continue;
			}
		}
		/* Cerramos el socket abierto cuando creamos el hilo. */
		this.socket.close();
	}

	// Método para procesar la solicitud enviada por clientAddr
	public void processRequestFromClient(byte[] data, InetSocketAddress clientAddr) throws IOException 
	{
		/* Creamos el buffer con la respuesta y sacamos el código de operación del mensaje. */
		ByteBuffer requestBuffer = ByteBuffer.wrap(data);
		byte operationCode = requestBuffer.get();
		
		/* Comprobamos que sea un código de solicitud. Si no lo es lanzamos una excepción.*/
		if (!DirectoryMessage.isRequestCode(operationCode)) throw new IOException("OpCode is not for a request.");
		
		/* Realizamos la operación que indica el código. */
		if (operationCode == DirectoryMessage.OP_REGISTRATION_REQUEST)
		{	
			/* Recuperamos el resto de información del mensaje. */
			int protocol = requestBuffer.getInt();
			int port = requestBuffer.getInt();
			
			/* Construimos un nuevo InetSocketAddress con la dirección del cliente y el puerto dado. Asumimos que la dirección del cliente es IpV4. */
			InetSocketAddress serverAddr = new InetSocketAddress(clientAddr.getAddress(), port);
			
			/* Asociamos la direccion del servidor al protocolo dado. Puede sustituir el anterior servidor asociado si el protocolo ya estaba en uso. */
			this.servers.put(protocol, serverAddr);
							
			/* Mensaje informativo. */
			System.out.println("\tClient  " + clientAddr.toString() + " registered itself as chat server with port " + port + " on chat protocol " + protocol + ".");
			
			/* Mandamos la confirmación al cliente. */
			this.sendRegistrationOK(clientAddr);
			
			/* Salto de linea para mejor formateo. */
			System.out.println();	
		}
		else if	(operationCode == DirectoryMessage.OP_SERVER_INFO_REQUEST)
		{
			/* Recuperamos el resto de la información del mensaje. */
			int protocol = requestBuffer.getInt();
			
			/* Recuperamos la dirección del servidor para ese protocolo. */
			InetSocketAddress chatServerAddr = this.servers.get(protocol);				
			
			/* Mensaje informativo. */
			System.out.println("\tClient " + clientAddr.toString() + " requested chat server address for protocol " + protocol + ".");
			
			/* Devolvemos un mensaje de que no hay servidor si no lo encontramos en el mapa. */
			if (chatServerAddr == null) 
			{
				System.out.println("\tNo server found for that protocol.");
				this.sendServerInfoNotFound(clientAddr);
			}
			/* En otro caso mandamos la información del servidor. */
			else this.sendServerInfoOk(chatServerAddr, clientAddr);
			
			/* Salto de linea para mejor formato. */
			System.out.println();
		}
	}

	/* Método para enviar el mensaje que indica que no se ha encontrado servidor para el protocolo pedido. */
	private void sendServerInfoNotFound(InetSocketAddress clientAddr) throws IOException 
	{
		/* Buffer donde construimos la respuesta y ponemos el OpCode correspondiente. */
		ByteBuffer responseBuffer = ByteBuffer.allocate(DirectoryMessage.SIZE_CONTROL_MSG);
		responseBuffer.put(DirectoryMessage.OP_SERVER_INFO_NOT_FOUND);
	
		/* Construimos el paquete de respuesta. */
		byte [] res = responseBuffer.array();
		DatagramPacket resPckt = new DatagramPacket(res, res.length, clientAddr);
		
		/* Enviamos el paquete de respuesta. */
		this.socket.send(resPckt);
		
		/* Mensaje informativo. */
		System.out.println("\tSending \"ServerInfoNotFound\" to client " + clientAddr.toString() + ".");
	}

	/* Método para enviar el mensaje con la infomación del servidor pedido. */
	private void sendServerInfoOk(InetSocketAddress serverAddr, InetSocketAddress clientAddr) throws IOException
	{
		/* Obtenemos el puerto y los 4 bytes de la ip del servidor. */
		int serverPort = serverAddr.getPort();
		byte[] serverIp = serverAddr.getAddress().getAddress();
		
		/* Buffer de respuesta. */
		ByteBuffer responseBuffer = ByteBuffer.allocate(DirectoryMessage.SIZE_IP_AND_PORT_MSG);
		
		/* Colocamos el OpCode, la dirección y el puerto. */
		responseBuffer.put(DirectoryMessage.OP_SERVER_INFO_OK);
		/* Suponemos trabajar con IpV4 ya que si fuese IpV6 habría desbordamiento del buffer. */
		responseBuffer.put(serverIp);
		responseBuffer.putInt(serverPort);
		
		/* Construimos el paquete de respuesta. */
		byte [] res = responseBuffer.array();
		DatagramPacket resPckt = new DatagramPacket(res, res.length, clientAddr);
		
		/* Enviamos la respuesta. */
		this.socket.send(resPckt);
		
		/* Mensaje informativo. */
		System.out.println("\tSending \"ServerInfoOk\" with address " + serverAddr.toString() + " to client " + clientAddr.toString() + ".");
	
	}

	/* Método para enviar el mensaje de confirmación de registro del protocolo. */
	private void sendRegistrationOK(InetSocketAddress clientAddr) throws IOException 
	{
		/* Buffer donde construimos la respuesta poniendo el OpCode correspondiente. */
		ByteBuffer responseBuffer = ByteBuffer.allocate(DirectoryMessage.SIZE_CONTROL_MSG);
		responseBuffer.put(DirectoryMessage.OP_REGISTRATION_OK);
		
		/* Construimos el paquete de respuesta. */
		byte [] res = responseBuffer.array();
		DatagramPacket resPckt = new DatagramPacket(res, res.length, clientAddr);
		
		/* Enviamos el paquete de respuesta. */
		this.socket.send(resPckt);
		
		/* Mensaje informativo. */
		System.out.println("\tSending \"RegistrationOk\" to client " + clientAddr.toString() + ".");
	}
}
