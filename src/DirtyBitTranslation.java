
/**
 * @author Ekta Arora (14115153)
 * description : This program translates the logical address to physical address
 * 				The size of Virtual memory is greater than the size of the Physical memory
 * 				This program also handles the dirty bit value of the page which is checked before the page is replaced using LRU algorithm.
 * 				If modbit of the page is 'W' this means the page has been modified and has to be written back to disk before being replaced
 * 				If modbit of the page is 'R' this means the page has not been modified and can be overwritten on the page replacement and no need to write back on the disk
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

public class DirtyBitTranslation {

	private final int SIZE = 256; // Size of page and frame
	private final int maxSixe = 128 * SIZE; // maximum size of the physical
											// memory

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
	private final String LOGICAL_ADDRESS_FILENAME = "addresses2.txt"; // file
																		// containing
																		// logical
																		// addresses
																		// and
																		// mod
																		// bits
	private final String BACKING_STORE_FILENAME = "BACKING_STORE.bin";

	private RandomAccessFile backingStore = null;

	private int physicalMemoryAddress;
	private int memPointer;
	private int framePointer;
	private int frameNumber;
	private int pageNumber;
	private int offset;
	private int signedValue;
	private int TLBHits;
	private int pageFaults;
	private int totalAddresses;

	private long timer[]; // Timer of the page residing in page table. For LRU
							// algorithm

	private int count; // Counter indicating the number of pages for which mod
						// bit was 'W' and they need to written back to the disk
						// before being replaced

	private String modBitArray[]; // array holding the mod bit values of each
									// page

	public DirtyBitTranslation() {
		memPointer = 0;
		framePointer = 0;
		frameNumber = 0;
		pageNumber = 0;
		offset = 0;
		signedValue = 0;
		TLBHits = 0;
		pageFaults = 0;
		totalAddresses = 0;
		count = 0;

		pageTable = new int[SIZE];
		physicalMemory = new byte[maxSixe];
		TLB = new HashMap<Integer, List<Integer>>();

		modBitArray = new String[SIZE];

		timer = new long[SIZE];

		// Initialize pageTable values to -1 to indicate that no page is
		// currently in page table
		for (int i = 0; i < SIZE; i++) {
			pageTable[i] = -1;
			timer[i] = System.currentTimeMillis();
		}
	}

	public static void main(String[] args) {
		new DirtyBitTranslation().init();
	}

	public void init() {

		BufferedReader br = null;
		String fileName = FILEPATH + LOGICAL_ADDRESS_FILENAME;
		String data = null;
		String contents[] = null;
		int logicalAddress = 0;

		File file = new File(fileName);
		FileReader fr;

		try {
			fr = new FileReader(file);
			br = new BufferedReader(fr);

			while ((data = br.readLine()) != null) {
				contents = data.split(" ");
				logicalAddress = Integer.valueOf(contents[0]);
				pageNumber = logicalAddress >> 8;
				offset = logicalAddress & 0x00FF;
				modBitArray[pageNumber] = contents[1];

				if (TLB.containsKey(pageNumber)) { // TLB hit
					TLBHits++;
					frameNumber = TLB.get(pageNumber).get(0);
					updateTLBCounter(pageNumber); // Update counter of the page
													// in TLB for LRU algorithm
				} else if (pageTable[pageNumber] > 0) {// Page table hit
					frameNumber = pageTable[pageNumber];
					updatePageTableTimer(pageNumber); // Update timer of the
														// page for LRU
														// algorithm
				} else {
					pageFaults++;
					handlePageFault();

					addToTLB(pageNumber, frameNumber); // add new page to TLB
					addToPageTable(pageNumber, frameNumber); // add new page to
																// page table
				}

				totalAddresses++;// counter mainting the total logical addresses
									// processed

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

		statistics();// print the statistics of the program

		// Print the number of pages for which the mod bit was 'W' and they had
		// to be written back to memory before being replaced
		System.out.println("Mod bit Count for pages with mod bit 'W' = " + count);
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
				checkModBit(victimPage); // Check if Victim should be replaced
											// in memory

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
			checkModBit(victimInTLB); // Check the modified bit of the Victim
										// page

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
			pageTable[newPage] = newFrame;
			timer[newPage] = System.currentTimeMillis();
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

	/**
	 * Check the victim page's mod bit. If mod bit is 'W' then write back the
	 * page to disk before replacing the victim page with new page update the
	 * counter if mod bit is 'W' and page has to be written back
	 * 
	 */
	private void checkModBit(int victimPage) {
		if (modBitArray[victimPage] != null) {
			if (modBitArray[victimPage].trim().equals("W")) {
				System.out.println("This page needs to be swapped back to disk");
				count++;
			}
		}
	}
}
