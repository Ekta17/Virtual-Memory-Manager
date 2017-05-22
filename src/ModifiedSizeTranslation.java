
/**
 * @author Ekta Arora (14115153)
 * description : This program translates the logical address to physical address
 * 				The size of Virtual memory is greater than the size of the Physical memory
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModifiedSizeTranslation {

	private final int SIZE = 256; // Size of page and frame
	private final int maxSixe = 128 * SIZE; // Maximum size of physical memory

	// TLB structure is as:
	// key is page number
	// there are two values associated with this one key
	// Value 1 = frame number of the page
	// Value 2 = counter indicating the number of times the page has been
	// accessed in TLB for LRU algorithm
	private Map<Integer, List<Integer>> TLB = null;

	private int pageTable[] = null;
	private byte physicalMemory[] = null;

	private final String FILEPATH = "./docs/";
	private final String LOGICAL_ADDRESS_FILENAME = "addresses.txt"; // file
																		// containing
																		// logical
																		// addresses
	private final String BACKING_STORE_FILENAME = "BACKING_STORE.bin";

	private RandomAccessFile backingStore = null;

	private int physicalMemoryAddress;
	private int framePointer;
	private int memPointer;
	private int frameNumber;
	private int pageNumber;
	private int offset;
	private int signedValue;
	private int TLBHits;
	private int pageFaults;
	private int totalAddresses;

	private long timer[]; // Timer of the page residing in page table. For LRU
							// algorithm

	public ModifiedSizeTranslation() {

		framePointer = 0;
		memPointer = 0;
		frameNumber = 0;
		pageNumber = 0;
		offset = 0;
		signedValue = 0;
		TLBHits = 0;
		pageFaults = 0;
		totalAddresses = 0;

		pageTable = new int[SIZE];
		physicalMemory = new byte[maxSixe];
		TLB = new HashMap<Integer, List<Integer>>();

		timer = new long[SIZE];

		for (int i = 0; i < SIZE; i++) {
			pageTable[i] = -1; // Initialize pageTable values to -1 to indicate
								// that no page is currently in page table
			timer[i] = System.currentTimeMillis(); // update the timer of each
													// page as current system
													// time
		}
	}

	public static void main(String[] args) {
		new ModifiedSizeTranslation().init();
	}

	public void init() {

		BufferedReader br = null;
		String fileName = FILEPATH + LOGICAL_ADDRESS_FILENAME;

		int logicalAddress = 0;

		File file = new File(fileName);
		FileReader fr;

		try {
			fr = new FileReader(file);
			br = new BufferedReader(fr);

			while ((logicalAddress = Integer.valueOf(br.readLine())) > 0) {

				pageNumber = logicalAddress >> 8;
				offset = logicalAddress & 0x00FF;

				if (TLB.containsKey(pageNumber)) {// TLB Hit
					TLBHits++;
					frameNumber = TLB.get(pageNumber).get(0);
					updateTLBCounter(pageNumber); // Update counter of the page
													// in TLB for LRU algorithm
				} else if (pageTable[pageNumber] > 0) { // Page Table Hit
					frameNumber = pageTable[pageNumber];
					updatePageTableTimer(pageNumber); // Update timer of the
														// page for LRU
														// algorithm
				} else { // Page Fault
					pageFaults++;

					handlePageFault();

					addToTLB(pageNumber, frameNumber); // add the page to TLB
					addToPageTable(pageNumber, frameNumber); // add the page to
																// page table
				}

				totalAddresses++; // counter mainting the total logical
									// addresses processed

				physicalMemoryAddress = frameNumber + offset;
				signedValue = physicalMemory[physicalMemoryAddress];

				System.out.println("Virtual address: " + logicalAddress + " Physical address: " + physicalMemoryAddress
						+ " Value: " + signedValue);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NumberFormatException e) {
		}

		statistics(); // print the statistics of the program
	}

	private void handlePageFault() {

		String fileName = FILEPATH + BACKING_STORE_FILENAME;

		try {
			backingStore = new RandomAccessFile(fileName, "r");

			backingStore.seek(pageNumber * SIZE);

			// Before loading a page to main memory check Main memory's size
			// If the size is full, first find victim page to be removed using
			// LRU algorithm and then add new page
			// If the size is not full, add the new page to Main memory
			if (memPointer == maxSixe) {
				int victimPage = getVictimPage();
				frameNumber = pageTable[victimPage];
				framePointer = (frameNumber / SIZE);
				memPointer = framePointer;

				backingStore.read(physicalMemory, memPointer, SIZE);

				pageTable[victimPage] = -1; // update page table for victim page
											// indicating it has been removed
				timer[victimPage] = 0;// update page timer of victim page
										// indicating it has been removed
				TLB.remove(victimPage); // update TLB for victim page indicating
										// it has been removed

			} else {
				backingStore.read(physicalMemory, memPointer, SIZE);
				frameNumber = framePointer * SIZE;
				framePointer++;
				memPointer += SIZE;
			}

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

		float pageFaultRate = (float) pageFaults / totalAddresses;
		float TLBHitRate = (float) TLBHits / totalAddresses;

		System.out.println("Page Fault Rate : " + pageFaultRate);
		System.out.println("TLB Hit Rate : " + TLBHitRate);
	}

	/**
	 * This function returns the victim page for page replacement using LRU
	 * algorithm. Victim will be the page which was not recently used by
	 * comparing each page in table against their value in timer
	 */
	private int getVictimPage() {

		int victimPage = 0;
		long currentTime = System.currentTimeMillis();

		for (int i = 0; i < timer.length; i++) {
			if (timer[i] < currentTime) {
				currentTime = timer[i];
				victimPage = i;
			}
		}

		return victimPage;
	}

	/**
	 * Function to add the new page to TLB Also, check the size of TLB and if
	 * the size exceeds maximum limit then do the page replacement using LRU
	 * algorithm
	 * 
	 * @param newPage
	 *            : new page's page number
	 * @param newFrame
	 *            : new page's frame number
	 */
	private void addToTLB(int newPage, int newFrame) {

		List<Integer> newValues = new ArrayList<Integer>();
		newValues.add(newFrame);
		newValues.add(1);

		// If TLB is full then remove the victim page first before adding any
		// new page in TLB
		if (TLB.size() >= 16) {// TLB full replace page
			int victimInTLB = getVictimTLBPage();
			TLB.remove(victimInTLB); // Remove Victim Page from TLB
		}

		// Add new page in TLB
		TLB.put(newPage, newValues);// Add new Page to TLB
	}

	/**
	 * Function to get the victim page from TLB based on the counter value of
	 * each page in TLB
	 */
	private int getVictimTLBPage() {

		int victim = -1;
		int min = Integer.MAX_VALUE;
		int key = -1;

		for (Map.Entry<Integer, List<Integer>> entry : TLB.entrySet()) {
			key = entry.getKey();
			if (min > TLB.get(key).get(1)) {
				min = TLB.get(key).get(1);
				victim = key;
			}
		}

		return victim;
	}

	/**
	 * Function to add the new page to page table
	 * 
	 * @param newPage
	 *            : new page's page number
	 * @param newFrame
	 *            : new page's frame number
	 */
	private void addToPageTable(int newPage, int newFrame) {
		if (pageTable[newPage] == -1) {
			pageTable[newPage] = newFrame; // update page table
			timer[newPage] = System.currentTimeMillis(); // update timer of the
															// page
		}
	}

	/**
	 * Page is there in TLB but accessed again, in case of TLB Hit. Hence update
	 * the counter of the Page for LRU algorithm
	 */
	private void updateTLBCounter(int page) {

		if (TLB.containsKey(page)) { // update counter of the page
			int localFrameNumber = TLB.get(page).get(0);
			int localCounter = TLB.get(page).get(1);

			List<Integer> newValues = new ArrayList<Integer>();
			newValues.add(localFrameNumber);
			newValues.add(localCounter + 1);

			TLB.replace(page, newValues);

		} // else page is not in TLB
	}

	/**
	 * Page is there in page table but accessed again Hence update the timer of
	 * the Page for LRU algorithm
	 */
	private void updatePageTableTimer(int page) {
		if (pageTable[page] != -1) {// Page is there in page table just update
									// the counter as it is accessed again
			timer[pageNumber] = System.currentTimeMillis();
		}
	}
}
