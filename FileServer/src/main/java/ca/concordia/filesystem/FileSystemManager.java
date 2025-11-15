//Version 1.4

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
    private boolean[] freeBlockList; // Bitmap for free blocks (false empty, true taken)

    public FileSystemManager(String filename, int totalSize) {
        // Initialize the file system manager with a file

        if (instance == null) {

            try {
                //TODO Initialize the file system


                //initialize the array containing all the File entries
                fileEntryDescriptors = new FEntry[MAXFILES];

                //initialize the free block list array
                freeBlockList = new boolean[MAXBLOCKS];

                //set first two blocks as space for metadata
                freeBlockList[0] = true;
                freeBlockList[1] = true;


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


            //4) write metadata starting from offset in disk
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



    // TODO: Add readFile, writeFile, deleteFile, ListFile
    public void deleteFile(String fileName) throws Exception {

        //SEARCH

        //Main variables one is to allocate the deleted file location and the other is to find it in FEntry
        int foundIndex = -1;
        FEntry filetoDelete = null;

        //Allocate the file's metadata
        for (int i = 0; i < fileEntryDescriptors.length; i++) {
            //Making sure that FEntry has data
            if (fileEntryDescriptors[i] != null) {
                if (fileEntryDescriptors[i].getFilename().equals(fileName)) {
                    //Save the data
                    foundIndex = i;
                    filetoDelete = fileEntryDescriptors[i];

                    break;
                }
            }
        }

        //Block that will check if foundIndex has changed or not
        if (foundIndex != 1){
            System.out.println("File" + fileName +"is found" );
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
        }
    }

    //Helper method : get the correct byte location, read the raw bytes and convert them into Fnode object
    private FNode readFNode (int blockIndex) throws Exception{

        //Physical byte offset
        int offset = blockIndex * BLOCK_SIZE;
        disk.seek(offset);


        //Read and store the two main
        int dataBlockIndex = disk.readInt();
        int nextFNodeIndex = disk.readInt();

        //Construct FNode
        FNode nextNode = new FNode(dataBlockIndex);
        nextNode.next(nextFNodeIndex);
        return nextNode;
    }

//    public byte[] readFile(String fileName) throws Exception{
//
//        getFilesize()
//
//        readByte
//
//    }

    public void writeFile(String fileName, byte[] contents) throws Exception{


        boolean fileExistflag = false;
        int numBytes = contents.length;
        int numBlocksNeeded = 0;
        boolean freeBlocksflag = false;
        int[] freeBlockIndices;
        freeBlockIndices = new int[freeBlockList.length];
        int numFreeBlocks = 0;

        int fileWRindex = 0;


        //1) check if file name exist
        for (int i=0; i<fileEntryDescriptors.length; i++){
            if(fileEntryDescriptors[i].getFilename().equals(fileName)){
                fileExistflag = true; //file exist
                fileWRindex = i; //save index of file we're writting too
                break;
            }
        }
        if (!fileExistflag){
            throw new Exception("Error: file does not exist");
        }

        //2) calculate needed space for contents (data block needed)

        if ((contents.length%BLOCK_SIZE) == 0){
            numBlocksNeeded = contents.length/BLOCK_SIZE;
        }
        else{
            numBlocksNeeded = contents.length/BLOCK_SIZE + 1;
        }

        //3) find available space on disk
        int count = 0;
        for (int i=0; i< freeBlockList.length; i++) {

            if (!freeBlockList[i]) { //if space is free
                freeBlockIndices[count] = i; //saves index of free space
                numFreeBlocks++; //counts the number of free blocks
                count++;

                if (numFreeBlocks == numBlocksNeeded){ //break if we found enough space
                    break;
                }
            }
        }
        if (numFreeBlocks == numBlocksNeeded){
            freeBlocksflag = true;
        }
        if (!freeBlocksflag){ //don't have enough free blocks to store this file
            throw new Exception("Error: no more space available");
        }


        //4) split contents and add chunk by chunk to the disk

        int offset = 0;
        byte[] temp;
        int position = 0;
        int bufferoffset = 0;
        int numBytesTocopy = 0;
        int remainingBytes = 0;

        for (int i = 0; i<numBlocksNeeded; i++){


           // FNode node = new FNode(freeBlockIndices[i]);





            //fileEntryDescriptors[fileWRindex].setFirstBlock();


            temp = new byte[BLOCK_SIZE]; //fresh buffer
            //write starting at offset in disk
            offset = freeBlockIndices[i] * BLOCK_SIZE;

            //temporarily store chunk of content in buffer
            System.arraycopy(contents,position, temp, bufferoffset , BLOCK_SIZE);
            position += 128;

            disk.seek(offset);
            disk.write(temp);
        }
    }


    String[] listFiles(){

        String[] listofFiles;

        listofFiles = new String[fileEntryDescriptors.length];

        for (int i=0; i<fileEntryDescriptors.length; i++){
            listofFiles[i] = fileEntryDescriptors[i].getFilename();
        }

        return listofFiles;
    }

}





