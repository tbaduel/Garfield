package fr.upem.net.other;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class FileInfo {
	public String filename;
	public int fileId;
	public int Size = 0;
	
	public FileInfo(String filename,  int fileId) {
		this.filename = filename;
		this.fileId = fileId;
	}
	
	public static boolean checkFileExist(String stringPath) {
		int maxFileSize = 50_000_000;
		try(FileChannel fc = FileChannel.open(Paths.get(stringPath), StandardOpenOption.READ)){
			long size = fc.size();
			if (size > maxFileSize) {
				System.out.println("Max file size = " + maxFileSize);
				return false;
			}
			return true;
		} catch (IOException e) {
			return false;
		}
	}
	
}
