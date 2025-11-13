//Version 1.1

package ca.concordia.filesystem;

import ca.concordia.filesystem.datastructures.FEntry;
import ca.concordia.filesystem.datastructures.FNode;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.locks.ReentrantLock;

public class FileSystemManager {

    private final int MAXFILES = 5;
    private final int MAXBLOCKS = 10;
    private final static FileSystemManager instance = null;
    private final RandomAccessFile disk;
    private final ReentrantLock globalLock = new ReentrantLock();

    private static final int BLOCK_SIZE = 128; // Example block size

    private FEntry[] inodeTable; // Array of inodes
    private boolean[] freeBlockList; // Bitmap for free blocks (0 empty, 1 taken)

    public FileSystemManager(String filename, int totalSize) {
        // Initialize the file system manager with a file

        if(instance == null) {

            try {
                //TODO Initialize the file system

                        //virtual memory mode and length
                        this.disk = new RandomAccessFile(filename, "rw");
                        this.disk.setLength(totalSize);

                        System.out.println("File " + filename + " created");
                        System.out.println(" ");
                        System.out.println("Total blocks: " + totalSize);

            }

            catch (IOException e){
                throw new IllegalArgumentException("ERROR");
            }


        } else {
            throw new IllegalStateException("FileSystemManager is already initialized.");

        }

    }

    public void createFile(String fileName) throws Exception {


        freeBlockList = new boolean[MAXBLOCKS]; //initialize size for freeBlocklist
        int indexfree=0;


        //1)run algorithm to check next available spot in blocklist using the bitmap
        for (int i=0; i<MAXBLOCKS; i++) {
            if(!freeBlockList[i]){ //if block is free
                indexfree = i; //save free index
            }
        }


        FNode newnode = new FNode(indexfree);
        while (newnode.next){

        }


            //2)create file entry
        FEntry newfile = new FEntry(fileName, BLOCK_SIZE, );


        //place metadata in array
        for (int i=0; i<inodeTable.length; i++) {

            if(inodeTable[i] == null) {
                inodeTable[i] = newfile;

            }
            else{
                System.out.println("no empty space");
            }
        }

        //set location in freeblocklist to 1 (spot now taken)

        //allocate space on virtual disk
        disk.write();


        throw new UnsupportedOperationException("Method not implemented yet.");
    }


    // TODO: Add readFile, writeFile and other required methods,
}
