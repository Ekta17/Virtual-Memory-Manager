
/**
 * @author Ekta Arora (14115153)
 * description : This program translates the logical address to physical address
 * 				The Size of Virtual memory is same as the size of the Physical memory
 */
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;

public class SameSizeTranslation {

	private final int SIZE = 256; // Page Size and Frame Size

	private Map<Integer, Integer> TLB = null;
	private int pageTable[] = null;
	private byte physicalMemory[] = null;

	private final String FILEPATH = "./docs/";
	private final String LOGICAL_ADDRESS_FILENAME = "addresses.txt"; // File
																		// with
																		// logical
																		// Addresses
	private final String BACKING_STORE_FILENAME = "BACKING_STORE.bin";

	private RandomAccessFile backingStore = null;

	private int physicalMemoryAddress;
	private int physicalMemoryAccess;
	private int totalFrames; // Variable for keeping the record of frames in
								// physical memory
	private int frameNumber;
	private int pageNumber;
	private int offset;
	private int signedValue;

	private int TLBHits;
	private int pageFaults;

	private int totalAddresses;

	public SameSizeTranslation() {
		physicalMemoryAccess = 0;
		totalFrames = 0;
		frameNumber = 0;
		pageNumber = 0;
		offset = 0;
		signedValue = 0;
		TLBHits = 0;
		pageFaults = 0;
		totalAddresses = 0;

		pageTable = new int[SIZE];
		physicalMemory = new byte[SIZE * SIZE];
		TLB = new HashMap<Integer, Integer>();

		// Initialize pageTable values to -1 to indicate that no page is
		// currently stored in page table
		for (int i = 0; i < SIZE; i++)
			pageTable[i] = -1;
	}

	public static void main(String[] args) {
		new SameSizeTranslation().init();
	}

	public void init() {

		BufferedReader br = null;
		String fileName = FILEPATH + LOGICAL_ADDRESS_FILENAME; // File with
																// Logical
																// Addresses
		int logicalAddress = 0;

		File file = new File(fileName);
		FileReader fr;

		try {
			fr = new FileReader(file);
			br = new BufferedReader(fr);

			while ((logicalAddress = Integer.valueOf(br.readLine())) > 0) {
				pageNumber = logicalAddress >> 8; // Extract Page number from
													// logical address
				offset = logicalAddress & 0x00FF; // Extract offset from logical
													// address

				if (TLB.containsKey(pageNumber)) { // Check TLB
					TLBHits++;
					frameNumber = TLB.get(pageNumber);
				} else if (pageTable[pageNumber] > 0) { // Check Page table
					frameNumber = pageTable[pageNumber];
				} else { // Not in TLB, not in Page table then page fault
					pageFaults++;
					handlePageFault();
				}

				totalAddresses++; // Counter keeping track of number of logical
									// addresses processed

				physicalMemoryAddress = frameNumber | offset;
				signedValue = physicalMemory[physicalMemoryAddress];

				System.out.println("Virtual address: " + logicalAddress + " Physical address: " + physicalMemoryAddress
						+ " Value: " + signedValue);

				checkAndUpdateTLB(pageNumber); // Check TLB for its size and
												// handle page replacement if
												// required
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NumberFormatException e) {
		}

		statistics(); // Print statistics on the screen

	}

	/**
	 * Function to handle page faults Called when the page is neither in TLB nor
	 * in page table. and hence the page should be brought from the Backing
	 * Store into physical memory
	 */
	private void handlePageFault() {

		String fileName = FILEPATH + BACKING_STORE_FILENAME;

		try {
			backingStore = new RandomAccessFile(fileName, "r"); // Open backing
																// store in read
																// mode

			backingStore.seek(pageNumber * SIZE); // NAvigate to position of the
													// page to be accessed
			backingStore.read(physicalMemory, physicalMemoryAccess, SIZE); // read
																			// the
																			// page
																			// from
																			// backing
																			// store
																			// and
																			// write
																			// to
																			// physical
																			// memory

			physicalMemoryAccess += SIZE; // Increment the pointer of physical
											// memory to store the next address
											// information as physical memory is
											// marked as an array of bytes
			pageTable[pageNumber] = totalFrames * SIZE; // update page table
			totalFrames++; // update frame counter
			frameNumber = pageTable[pageNumber];

			backingStore.close();

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void statistics() {

		double pageFaultRate = (double) pageFaults / totalAddresses;
		double TLBHitRate = (double) TLBHits / totalAddresses;

		System.out.println("Page Fault Rate : " + pageFaultRate);
		System.out.println("TLB Hit Rate : " + TLBHitRate);
	}

	/**
	 * This function handles the page replacement in TLB
	 */
	public void checkAndUpdateTLB(int page) {
		if (TLB.size() <= 16) {
			if (TLB.containsKey(page)) {
				TLB.remove(page);
				TLB.put(page, frameNumber);
			}
			TLB.put(page, frameNumber);
		} else {
			TLB.remove(page);
		}
	}

}
