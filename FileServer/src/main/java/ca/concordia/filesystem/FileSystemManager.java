//Version 1.4

package ca.concordia.filesystem;

import ca.concordia.filesystem.datastructures.FEntry;

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

        if(instance == null) {

            try {
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

            }

            catch (IOException e){
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







    // TODO: Add readFile, writeFile and other required methods,
}
