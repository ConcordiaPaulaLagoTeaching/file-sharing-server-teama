//Version 1.5

package ca.concordia.filesystem;

import ca.concordia.filesystem.datastructures.FEntry;
import ca.concordia.filesystem.datastructures.FNode;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

public class FileSystemManager {

    private final int FILEENTRYSIZE = 15; //string (11 bytes) + 2xshort (4bytes)
    private final int MAXFILES = 5;
    private final int MAXBLOCKS = 10;
    private final int DATASIZE = 128;
    private final static FileSystemManager instance = null;
    private final RandomAccessFile disk;
    private final ReentrantLock globalLock = new ReentrantLock();

    private static final int BLOCK_SIZE = 128; // Example block size
    private final int DATA_BLOCK_START_INDEX = 2; // Data blocks start at Physical Block 2

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


                //saved space for metadata
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
        int nextNode = 0;

        for (int i = 0; i<numBlocksNeeded; i++){

            temp = new byte[BLOCK_SIZE]; //fresh buffer
            //write starting at offset in disk
            offset = freeBlockIndices[i] * BLOCK_SIZE;

            /// ////////////////////////////////////
            if (i<numBlocksNeeded-1){ //if we have not reached the last block
                nextNode = freeBlockIndices[i+1]; //next node points to the next blockIndex thats free
            }
            else{
                nextNode = -1; //next node of final block is -1 (end of file entry)
            }
            FNode node = new FNode(nextNode);
            /// /////////////////////////////////////

            remainingBytes = contents.length - position;

            if (i<numBlocksNeeded-1){
                numBytesTocopy = DATASIZE;
            }
            else if (i == numBlocksNeeded-1){

                numBytesTocopy = remainingBytes;
            }

            //temporarily store chunk of content in buffer
            System.arraycopy(contents,position, temp, bufferoffset ,numBytesTocopy);
            position += numBytesTocopy;

            disk.seek(offset);
            disk.write(temp);
            //System.out.println(Arrays.toString(temp));
        }

        //update in file entries
        FEntry fileWR = fileEntryDescriptors[fileWRindex]; //reference to file were writing in
        fileWR.setFirstBlock((short)freeBlockIndices[0]);
        fileWR.setFilesize((short)contents.length);

        //set blocks that are used to true in the freeblocklist
        for (int i=0; i<numBlocksNeeded; i++){
            freeBlockList[(freeBlockIndices[i])] = true;
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
            throw new Exception("Error: " + fileName + " does not exist");
        }

        //Start reading process
        else {
            int currentBlockIndex = filetoRead.getFirstBlock();

            //Utilize dynamic memory allocation
            byte [] readFileData = new byte[filetoRead.getFilesize()];
            int readFilePos = 0;


            while(currentBlockIndex != -1){

                int remainByteFile = filetoRead.getFilesize() - readFilePos;
                int remainByteBlock;

                if (remainByteFile < BLOCK_SIZE){

                    remainByteBlock = remainByteFile;
                }

                else {
                    remainByteBlock = BLOCK_SIZE;
                }

                //Retrieve FNode object
                FNode currentNode = readFNode(currentBlockIndex);

                int currentBlockContent = currentNode.getBlockIndex();

                //Physical byte offset
                //Offset starts after reading block 0 (FEntry) and block 1 (FNodes)
                int offset = (currentBlockContent + DATA_BLOCK_START_INDEX) * BLOCK_SIZE;
                disk.seek(offset);

                //Read one block (128 byte) at a time
                byte [] blockData = new byte[BLOCK_SIZE];
                int readByte = disk.read(blockData, 0, remainByteBlock);

                if (readByte == -1 ){
                    break;
                }

                //Copy the temporary byte data into final byte data
                System.arraycopy(blockData, 0, readFileData, readFilePos, readByte);
                readFilePos = readFilePos + readByte;

                currentBlockIndex = currentNode.getNext(); //update index for next block index


            }

            return readFileData;
        }
    }

    public String[] listFiles() throws Exception {

        //LIST
        String[] lsFiles;

        lsFiles = new String[fileEntryDescriptors.length];


        for (int i = 0; i < MAXFILES; i++) {

            if (fileEntryDescriptors[i] != null) {
                lsFiles[i] = (fileEntryDescriptors[i].getFilename().trim());
                System.out.println(fileEntryDescriptors[i].getFilename());
            }
        }
        return lsFiles;
    }
}


