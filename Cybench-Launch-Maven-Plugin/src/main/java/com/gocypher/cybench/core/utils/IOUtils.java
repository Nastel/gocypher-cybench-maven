/*
 * Copyright (C) 2020, K2N.IO.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 */

package com.gocypher.cybench.core.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;


public class IOUtils {
	private static Logger LOG = LoggerFactory.getLogger(IOUtils.class);
	private static int randomFileChunkSize = 65536;
	private static long fileSizeMultiplierPerChunkSize = 16384;
	private static long fileSizeSmallMultiplierPerChunkSize = 2048;
	// private static long fileSizeMultiplierPerChunkSize = 1024 ;
	private static String FILE_NAME_AS_SRC = "binary.bin";
	private static String FILE_NAME_AS_SRC_FOR_SMALL_CASES = "small_binary.bin";
	private static String FILE_NAME_AS_DST = "output-binary-test.txt";
	private static String FILE_NAME_AS_DST_FOR_SMALL_CASES = "output_small_binary_test.bin";


	public static void removeTestDataFiles() {
		removeFile(new File(FILE_NAME_AS_SRC));
		removeFile(new File(FILE_NAME_AS_DST));
		removeFile(new File(FILE_NAME_AS_SRC_FOR_SMALL_CASES));
		removeFile(new File(FILE_NAME_AS_DST_FOR_SMALL_CASES));
	}


	public static void removeFile(File file) {
		try {
			if (file != null && file.exists()) {
				file.delete();
			}
		} catch (Exception e) {
			LOG.error("Error on removing file={}", file, e);
		}
	}

	public static void storeResultsToFile(String fileName, String content) {
		FileWriter file = null;
		try {
			File cFile = new File(fileName);
			File pFile = cFile.getParentFile();
			boolean exists = pFile.exists();
			if (!exists) {
				if (!pFile.mkdir()) {
					throw new IOException("Could not create folder=" + pFile);
				}
			}
			file = new FileWriter(fileName);
			file.write(content);
			file.flush();
		} catch (Exception e) {
			LOG.error("Error on saving to file={}", fileName, e);
		} finally {
			close(file);
		}
	}

	public static void close(Closeable obj) {
		try {
			if (obj != null) {
				obj.close();
			}
		} catch (Throwable e) {
			LOG.error("Error on close: obj={}", obj,e);
		}		
	}


	public static long getHugeRandomBinaryFileSizeInBytes() {
		return randomFileChunkSize * fileSizeMultiplierPerChunkSize;
	}

	public static long getSmallRandomBinaryFileSizeInBytes() {
		return randomFileChunkSize * fileSizeSmallMultiplierPerChunkSize;
	}

	public static String getReportsPath(String reportsFolder, String reportFileName){
		if (reportsFolder == null || reportsFolder.isEmpty()) {
			return "." + File.separator + "reports" + File.separator+reportFileName;
		}
		return reportsFolder+reportFileName ;
	}

}
