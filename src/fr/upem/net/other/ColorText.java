package fr.upem.net.other;

public class ColorText {

	public final static String BLUE = "\033[1;34m";
	public final static String END = "\033[0;m";
	public final static String GREEN = "\033[1;32m";
	public final static String PURPLE = "\033[1;35m";
	public final static String WHITE = "\033[0;39m";
	public static String colorize(String color, String text) {
		return color + text + END;
	}
	
	public static void start(String color) {
		System.out.println(color);
	}
	
	public static void end() {
		System.out.println(END);
	}
}
