//Version 1.0

package ca.concordia.server;
import ca.concordia.filesystem.FileSystemManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;


public class FileServer {

    private FileSystemManager fsManager;
    private int port;
    public FileServer(int port, String fileSystemName, int totalSize){
        // Initialize the FileSystemManager
        FileSystemManager fsManager = new FileSystemManager(fileSystemName,
                10*128 );
        this.fsManager = fsManager;
        this.port = port;
    }

    public void start(){
        try (ServerSocket serverSocket = new ServerSocket(12345)) {
            System.out.println("Server started. Listening on port 12345...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Handling client: " + clientSocket);
                try (
                        BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                        PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)
                ) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("Received from client: " + line);
                        String[] parts = line.split(" ");
                        String command = parts[0].toUpperCase();
                        byte[] stringtoByte;
                        String writeContent;

                        switch (command) {
                            case "CREATE":
                                fsManager.createFile(parts[1]);
                                writer.println("SUCCESS: File '" + parts[1] + "' created.");
                                writer.flush();
                                break;
                            //TODO: Implement other commands READ, WRITE, DELETE, LIST
                            case "DELETE":
                                fsManager.deleteFile(parts[1]);
                                writer.println("SUCCESS: File '" + parts[1] + "' deleted.");
                                writer.flush();
                                break;

                            case "WRITE":

                                //assembles all the words to write to the file
                                writeContent = String.join(" ", Arrays.copyOfRange(parts, 2, parts.length));

                                //convert String to array of bytes in order to pass as parameter to writeFile function
                                stringtoByte = writeContent.getBytes(StandardCharsets.UTF_8);
                                fsManager.writeFile(parts[1], stringtoByte);
                                writer.println("Successfully wrote " + stringtoByte.length + " bytes to file: '" + parts[1] + "'.");
                                writer.flush();
                                break;

                            case "READ":



                                break;
                            case "LIST":
                                fsManager.listFiles();
                                writer.println("SUCCESSFULLY READ FILES");

                                writer.println(Arrays.toString(fsManager.listFiles()));
                                writer.flush();
                                break;
                            case "QUIT":
                                writer.println("SUCCESS: Disconnecting.");
                                return;
                            default:
                                writer.println("ERROR: Unknown command.");
                                break;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    try {
                        clientSocket.close();
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Could not start server on port " + port);
        }
    }

}
