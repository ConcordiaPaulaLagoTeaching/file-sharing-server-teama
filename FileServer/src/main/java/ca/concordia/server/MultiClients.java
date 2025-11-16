//This is a Runnable interface

//Made new class to take information about the different clients encapsulated within a Runnable

package ca.concordia.server;

import ca.concordia.filesystem.FileSystemManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class MultiClients implements Runnable{



    private Socket clientSocket;
    private FileSystemManager fsManager;
    int count = 0;

    public MultiClients (Socket clientSocket, FileSystemManager fsManager){

        this.clientSocket = clientSocket;
        this.fsManager = fsManager;


    }

    //Everything from here will be Threaded
    @Override
    public void run(){


            try (
                    BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)
            ) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("Received from client: " + (count++) + line);
                    String[] parts = line.split(" ");
                    String command = parts[0].toUpperCase();
                    byte[] bytearray;

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


                        case "READ":

                            String filename = parts[1];

                            // Read file data
                            byte[] fileData = fsManager.readFile(filename);


                            String content = new String(fileData, StandardCharsets.UTF_8);

                            // Send content to client
                            writer.println("The message for " + filename + " is :" + content);

                            writer.flush();
                            break;

                        case "LIST":

                            String [] files = fsManager.listFiles();

                            for (int i = 0; i < files.length; i++){

                                if (files[i] != null){
                                    writer.println("Name: " + Arrays.toString(files));
                                }

                            }
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

    }

