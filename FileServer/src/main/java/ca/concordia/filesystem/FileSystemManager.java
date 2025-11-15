//Version 1.5

package ca.concordia.filesystem;

import ca.concordia.filesystem.datastructures.FEntry;
import ca.concordia.filesystem.datastructures.FNode;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.locks.ReentrantLock;

public class FileSystemManager {

    private final int FILEENTRYSIZE = 15; //string (11 bytes) + 2xshort (4bytes)
    private final int MAXFILES = 5;
    private final int MAXBLOCKS = 10;
    private final static FileSystemManager instance = null;
    private final RandomAccessFile disk;
    private final ReentrantLock globalLock = new ReentrantLock();

    private static final int BLOCK_SIZE = 128; // Example block size

    private FEntry[] fileEntryDescriptors; // Array of inodes
    private boolean[] freeBlockList; // Bitmap for free blocks (0 empty, 1 taken)

    public FileSystemManager(String filename, int totalSize) {
        // Initialize the file system manager with a file

        if (instance == null) {

            try {
                //TODO Initialize the file system


                //initialize the array containing all the File entries
                fileEntryDescriptors = new FEntry[MAXFILES];

                //initialize the free block list array
                freeBlockList = new boolean[MAXBLOCKS];


                //virtual memory mode and length
                this.disk = new RandomAccessFile(filename, "rw");
                this.disk.setLength(totalSize);

                System.out.println("File " + filename + " created");
                System.out.println(" ");
                System.out.println("Total blocks: " + totalSize);

            } catch (IOException e) {
                throw new IllegalArgumentException("ERROR");
            }


        } else {
            throw new IllegalStateException("FileSystemManager is already initialized.");

        }

    }

    public void createFile(String fileName) throws Exception {

        int saveIndex = -1;
        int diskOffset = 0;

        //1) create a new file entry
        FEntry newFile = new FEntry(fileName, (short)0, (short)-1);

        try {
            //2) check the file entry array to see if there's available space
            for (int i = 0; i < fileEntryDescriptors.length; i++) {
                if (fileEntryDescriptors[i] == null) {
                    saveIndex = i;
                    break;
                }
            }
            if (saveIndex == -1) {
                throw new Exception("Error: No more space for files");
            }
            else {
                fileEntryDescriptors[saveIndex] = newFile;

            }

            //3) calculate offset to write the next file entry
            diskOffset = saveIndex * FILEENTRYSIZE;


            //3) write metadata starting from offset in disk
            disk.seek(diskOffset);



            //add padding to name of file to keep file entry structure in order
            String filenamePadded;
            int padding = 0;
            filenamePadded = newFile.getFilename();

            if (filenamePadded.length() < 11) {

                padding = 11 - filenamePadded.length();
                for (int i = 0; i < padding; i++) {
                    filenamePadded = filenamePadded + " ";
                }
            }

            disk.writeBytes(filenamePadded);
            disk.writeShort(newFile.getFilesize());
            disk.writeShort(newFile.getFirstBlock());

        }

        catch (IOException e){
            if (saveIndex != -1) {
                fileEntryDescriptors[saveIndex] = null;
            }

            throw new Exception("Disk write failed");
        }
    }


    public void deleteFile(String fileName) throws Exception {

        //SEARCH

        //Main variables one is to allocate the deleted file location and the other is to find it in FEntry
        int foundIndex = -1;
        FEntry filetoDelete = null;

        //Allocate the file's metadata
        for (int i = 0; i < fileEntryDescriptors.length; i++) {
            //Making sure that FEntry has data
            if (fileEntryDescriptors[i] != null) {
                if (fileEntryDescriptors[i].getFilename().trim().equals(fileName)) {
                    //Save the data
                    foundIndex = i;
                    filetoDelete = fileEntryDescriptors[i];

                    break;
                }
            }
        }

        //Block that will check if foundIndex has changed or not
        if (foundIndex == -1){
            System.out.println("File" + fileName +"is not found" );
        }
        //Start deletion process
        //Method : getFirstBlock
        else {
            int currentBlockIndex = filetoDelete.getFirstBlock();
            while (currentBlockIndex != -1){
                //Retrieve FNode object
                FNode currentNode = readFNode (currentBlockIndex);

                freeBlockList[currentNode.getBlockIndex()] = false; //free the data block
                freeBlockList[currentBlockIndex] = false; //free the metadata

                currentBlockIndex = currentNode.getNext(); //update index for next block index
            }
            fileEntryDescriptors[foundIndex] = null; //complete deletion

            //Physical byte offset
            int offset = foundIndex * FILEENTRYSIZE;
            disk.seek(offset);

            for (int i = 0; i < FILEENTRYSIZE; i++){
                disk.writeByte(0);
            }


        }
    }

    //Helper method : get the correct byte location, read the raw bytes and convert them into Fnode object
    private FNode readFNode (int blockIndex) throws Exception{

        //Physical byte offset
        int offset = blockIndex *  BLOCK_SIZE;
        disk.seek(offset);


        //Read and store the two main
        int dataBlockIndex = disk.readInt();
        int nextFNodeIndex = disk.readInt();

        //Construct FNode
        FNode nextNode = new FNode(dataBlockIndex);
        nextNode.next(nextFNodeIndex);
        return nextNode;
    }

    public byte[] readFile(String fileName) throws Exception{

        //READ

        //Main variables one is to allocate the index of the file to read and the other is to find it in FEntry
        int foundIndex_read = -1;
        FEntry filetoRead = null;


        //Allocate the file's metadata
        for (int i = 0; i < fileEntryDescriptors.length; i++){
            //Making sure that Fentry has data
            if (fileEntryDescriptors[i] != null){
                if(fileEntryDescriptors[i].getFilename().trim().equals(fileName)){
                    //Save the data
                    foundIndex_read = i;
                    filetoRead = fileEntryDescriptors[i];
                    break;
                }
            }
        }

        //Block that will check if foundIndex has changed or not
        if (foundIndex_read == -1){
            throw new Exception("Error : File is not found");
        }

        //Start reading process
        else {
              int currentBlockIndex = filetoRead.getFirstBlock();

            //Utilize dynamic memory allocation
            byte [] readFileData = new byte[filetoRead.getFilesize()];
            int readFilePos = 0;

              while(currentBlockIndex != -1){
                  //Retrieve FNode object
                  FNode currentNode = readFNode(currentBlockIndex);

                  int currentBlockContent = currentNode.getBlockIndex();

                  //Physical byte offset
                  int offset = currentBlockContent * BLOCK_SIZE;
                  disk.seek(offset);

                  //Read one block (128 byte) at a time
                  byte [] blockData = new byte[BLOCK_SIZE];
                  int readByte = disk.read(blockData);


                  //Copy the temporary byte data into final byte data
                  System.arraycopy(blockData, 0, readFileData, readFilePos, readByte);
                  readFilePos = readFilePos + readByte;

                  currentBlockIndex = currentNode.getNext(); //update index for next block index
              }
            return readFileData;
        }
    }






}


