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
    private final int NODESIZE = 8;
    private final int DATABLOCKSTART = 2;
    private final static FileSystemManager instance = null;
    private final RandomAccessFile disk;
    private final ReentrantLock globalLock = new ReentrantLock();
    private final ReentrantLock fileserverLock = globalLock;


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


                //saved space in first two blocks for metadata
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

    private void enterFEntry(int index, FEntry file) throws IOException {

        //calculate offset to write the next file entry
        int diskOffset = index * FILEENTRYSIZE;

        //write metadata starting from offset in disk
        disk.seek(diskOffset);
        String filenamePadded;
        int padding = 0;
        filenamePadded = file.getFilename();

        if (filenamePadded.length() < 11) {

            padding = 11 - filenamePadded.length();
            for (int i = 0; i < padding; i++) {
                filenamePadded = filenamePadded + " ";
            }
        }
        //7) write to disk
        disk.writeBytes(filenamePadded);
        disk.writeShort(file.getFilesize());
        disk.writeShort(file.getFirstBlock());
    }





    public void createFile(String fileName) throws Exception {

        fileserverLock.lock();
        int saveIndex = -1;

        //1) check if file already exist
        for (int i = 0; i < fileEntryDescriptors.length; i++) {
            if ((fileEntryDescriptors[i] != null) && ((fileEntryDescriptors[i].getFilename().trim()).equals(fileName))) {
                throw new Exception("File: " + fileName + " already exist");
            }
        }

        //2) create a new file entry
        FEntry newFile = new FEntry(fileName, (short) 0, (short) -1);

        try {

            //3) check the file entry array to see if there's available space
            for (int i = 0; i < fileEntryDescriptors.length; i++) {
                if (fileEntryDescriptors[i] == null) {
                    saveIndex = i;
                    break;
                }
            }

            if (saveIndex == -1) {
                throw new Exception("Error: No more space for files");
            } else {
                fileEntryDescriptors[saveIndex] = newFile;
            }

            enterFEntry(saveIndex, newFile);

        } catch (IOException e) {
            if (saveIndex != -1) {
                fileEntryDescriptors[saveIndex] = null;
            }

            throw new Exception("Disk write failed");
        }

        fileserverLock.unlock();
    }


    public void deleteFile(String fileName) throws Exception {

        fileserverLock.lock();
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
        if (foundIndex == -1) {
            System.out.println("File" + fileName + "is not found");
        }
        //Start deletion process
        //Method : getFirstBlock
        else {
            int currentBlockIndex = filetoDelete.getFirstBlock();
            while (currentBlockIndex != -1) {
                //Retrieve FNode object
                FNode currentNode = readFNode(currentBlockIndex);

                freeBlockList[currentNode.getBlockIndex()] = false; //free the data block
                freeBlockList[currentBlockIndex] = false; //free the metadata

                currentBlockIndex = currentNode.getNext(); //update index for next block index
            }
            fileEntryDescriptors[foundIndex] = null; //complete deletion

            //Physical byte offset
            int offset = foundIndex * FILEENTRYSIZE;
            disk.seek(offset);

            for (int i = 0; i < FILEENTRYSIZE; i++) {
                disk.writeByte(0);
            }
        }
        fileserverLock.unlock();
    }

    public void writeFile(String fileName, byte[] contents) throws Exception {

        fileserverLock.lock();

        boolean fileExistflag = false;
        int numBlocksNeeded = 0;
        boolean freeBlocksflag = false;
        int[] freeBlockIndices;
        freeBlockIndices = new int[freeBlockList.length];
        int numFreeBlocks = 0;
        int fileWRindex = 0;

        //1) check if file name exist
        for (int i = 0; i < fileEntryDescriptors.length; i++) {
            if ((fileEntryDescriptors[i] != null) && ((fileEntryDescriptors[i].getFilename().trim()).equals(fileName))) {
                fileExistflag = true; //file exist
                fileWRindex = i; //save index of file we're writting too
                break;
            }
        }
        if (!fileExistflag) {
            throw new Exception("Error: file does not exist");
        }

        //2) overwrite file if it exists on the disk
        FEntry existingfile = fileEntryDescriptors[fileWRindex];
        int existingfileFnodeIndex = existingfile.getFirstBlock(); //first node

        //reset Nodes
        while (existingfileFnodeIndex != -1) { //until last node is reached (node.getNext() is -1)
            //start at currentNode node of existing file
            int offsetOfNode = BLOCK_SIZE + (existingfileFnodeIndex * NODESIZE); //Nodes start at block 1
            disk.seek(offsetOfNode);
            int thisNodeIndex = disk.readInt(); //read 4bytes of the current existing node
            int nextNodeIndex = disk.readInt(); //read 4bytes of the next of existing node

            FNode node = new FNode(thisNodeIndex); //reference to current node
            node.setNext(nextNodeIndex);
            //free data block on free block list
            freeBlockList[node.getBlockIndex() + DATABLOCKSTART] = false;

            //create an object to explicitly clear the Node metadata
            FNode clearFNode = new FNode(-1);
            clearFNode.setNext(-1);
            //clear location of existing file node
            disk.seek(offsetOfNode);
            disk.writeInt(clearFNode.getBlockIndex());
            disk.writeInt(clearFNode.getNext());

            existingfileFnodeIndex = node.getNext(); //go to next node of existing file
        }
        //reset File entry
        existingfile.setFirstBlock((short) -1);
        existingfile.setFilesize((short) 0);
        enterFEntry(fileWRindex, existingfile); //clear file entry on disk

        //3) calculate needed space for contents (numBlocksNeeded)
        if ((contents.length % BLOCK_SIZE) == 0) { //perfectly fit in blocks
            numBlocksNeeded = contents.length / BLOCK_SIZE;
        } else {
            numBlocksNeeded = contents.length / BLOCK_SIZE + 1;
        }

        //4) find available space on disk
        int count = 0;
        for (int i = 0; i < freeBlockList.length; i++) {

            if (!freeBlockList[i]) { //if space is free
                freeBlockIndices[count] = i - DATABLOCKSTART; //saves index of free space
                numFreeBlocks++; //counts the number of free blocks
                count++;
                if (numFreeBlocks == numBlocksNeeded) { //break if we found enough space
                    break;
                }
            }
        }
        if (numFreeBlocks == numBlocksNeeded) {
            freeBlocksflag = true;
        }
        if (!freeBlocksflag) { //don't have enough free blocks to store this file
            throw new Exception("Error: no more space available");
        }

        //5) check FNode availability
        int freeNodes = 0;
        int[] freeNodeIndices = new int[MAXBLOCKS];

        for(int i=0; i<MAXBLOCKS; i++){
            FNode node = readFNode(i);

            if (node.getBlockIndex() < 0){
                freeNodeIndices[freeNodes++] = i;
                if (freeNodes == numBlocksNeeded){ //if enough nodes were found, break
                    break;
                }
            }
        }

        if (freeNodes != numBlocksNeeded){
            throw new Exception("Error: no more space available");
        }

        //6) split contents and adds the data chunk by chunk to the disk
        int offsetWR = 0;
        int position = 0;
        int numBytesTocopy = 0;
        int remainingBytes = 0;
        int nextNodeindex = 0;
        //for the buffer
        byte[] temp;

        for (int i = 0; i < numBlocksNeeded; i++) {

            //write Node meta data to disk------------------------------------------------------------
            temp = new byte[BLOCK_SIZE]; //fresh buffer

            int currentNodeindex = freeNodeIndices[i];

            if (i < numBlocksNeeded - 1) { //if we have not reached the last block
                nextNodeindex = freeNodeIndices[i + 1]; //next node points to the next node Index thats free
            } else {
                nextNodeindex = -1; //next node of final block is -1 (end of file entry)
            }
            int datablockindex = freeBlockIndices[i];

            FNode node = new FNode(datablockindex);
            node.setNext(nextNodeindex);

            int offsettemp = BLOCK_SIZE + (currentNodeindex * NODESIZE);
            disk.seek(offsettemp);
            //write the 8 bytes for the nodes
            disk.writeInt(node.getBlockIndex());
            disk.writeInt(node.getNext());

            //write starting at offset in disk where data starts------------------------------------
            offsetWR = (freeBlockIndices[i] + DATABLOCKSTART) * BLOCK_SIZE;

            remainingBytes = contents.length - position;

            if (i < numBlocksNeeded - 1) {
                numBytesTocopy = DATASIZE; //for all full blocks (128 bytes)
            } else if (i == numBlocksNeeded - 1) {

                numBytesTocopy = remainingBytes; //remaining bytes
            }
            //temporarily store chunk of content in buffer
            System.arraycopy(contents, position, temp, 0, numBytesTocopy);
            position += numBytesTocopy;

            disk.seek(offsetWR);
            disk.write(temp,0,numBytesTocopy);
            //System.out.println(Arrays.toString(temp));
        }
        //update in file entries on the disk-------------------------------------------------------
        FEntry fileWR = fileEntryDescriptors[fileWRindex]; //reference to file were writing in
        fileWR.setFirstBlock((short) freeNodeIndices[0]); //first block points to first node
        fileWR.setFilesize((short) contents.length);
        enterFEntry(fileWRindex, fileWR); //enter file into disk
        //set blocks that are used to true in the free blocklist
        for (int i = 0; i < numBlocksNeeded; i++) {
            freeBlockList[(freeBlockIndices[i]) + DATABLOCKSTART] = true;
        }

        fileserverLock.unlock();
    }






    //Helper method : get the correct byte location, read the raw bytes and convert them into Fnode object
    private FNode readFNode (int nodeIndex) throws Exception{

        //Physical byte offset
        int offset = (nodeIndex * NODESIZE) +  BLOCK_SIZE;
        disk.seek(offset);


        //Read and store the two main
        int dataBlockIndex = disk.readInt();
        int nextFNodeIndex = disk.readInt();

        //Construct FNode
        FNode nextNode = new FNode(dataBlockIndex);
        nextNode.setNext(nextFNodeIndex);
        return nextNode;
    }

    public byte[] readFile(String fileName) throws Exception{

        fileserverLock.lock();
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

            fileserverLock.unlock();
            return readFileData;
        }


    }

    public String[] listFiles() throws Exception {

        fileserverLock.lock();
        //LIST
        String[] lsFiles;

        lsFiles = new String[fileEntryDescriptors.length];


        for (int i = 0; i < MAXFILES; i++) {

            if (fileEntryDescriptors[i] != null) {
                lsFiles[i] = (fileEntryDescriptors[i].getFilename().trim());
                System.out.println(fileEntryDescriptors[i].getFilename());
            }
        }
        fileserverLock.unlock();
        return lsFiles;
    }
}


