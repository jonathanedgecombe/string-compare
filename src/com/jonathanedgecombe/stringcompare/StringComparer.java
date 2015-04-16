package com.jonathanedgecombe.stringcompare;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

/**
 * A class for calculating visual distance between rendered strings.
 * @author Jonathan Edgecombe
 */
public final class StringComparer {
	/**
	 * If enabled, save a visual representation of the similarity for debugging purposes.
	 */
	private final static boolean DEBUG = true;

	/**
	 * The threshold used to determine between modifications and insertions/deletions.
	 */
	private final static float THRESHOLD = 0.085f;

	/**
	 * Rendering settings.
	 */
	private final Font font;
	private final Object aliasing;
	private final Color background, foreground;

	/**
	 * The font rendering context used for calculating the bounds of rendered strings.
	 */
	private final FontRenderContext context;

	/**
	 * Constructs a new string comparer with the given settings.
	 * @param font The font to render with.
	 * @param aliasing The aliasing setting for the text. Use RenderingHints.VALUE_TEXT_ANTIALIASING_*.
	 * @param background The background color to render with.
	 * @param foreground The foreground (text) color to render with.
	 */
	public StringComparer(Font font, Object aliasing, Color background, Color foreground) {
		this.font = font;
		this.aliasing = aliasing;
		this.background = background;
		this.foreground = foreground;

		Graphics2D g = (Graphics2D) new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).getGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, aliasing);
		this.context = g.getFontRenderContext();
	}

	/**
	 * Renders a string using the current settings.
	 * @param string
	 * @return The rendered string.
	 */
	public BufferedImage render(String string) {
		Rectangle2D r = font.getStringBounds(string, context);
		BufferedImage img = new BufferedImage((int) Math.ceil(r.getWidth()), (int) Math.ceil(r.getHeight()), BufferedImage.TYPE_INT_ARGB);

		Graphics2D gr = (Graphics2D) img.getGraphics();
		gr.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, aliasing);

		gr.setColor(background);
		gr.fillRect(0, 0, img.getWidth(), img.getHeight());

		gr.setColor(foreground);
		gr.setFont(font);
		gr.drawString(string, (int) r.getX(), (int) -r.getY());

		return img;
	}

	/**
	 * Returns the visual distance between two strings.
	 * @param a
	 * @param b
	 * @return The visual distance between the two strings.
	 */
	public float compare(String a, String b) {
		BufferedImage imgA = render(a);
		return compare(imgA, b);
	}

	/**
	 * Returns the visual distance between a pre-rendered string and a string.
	 * @param a A pre-rendered string.
	 * @param b A string to compare with.
	 * @return The visual distance between the pre-rendered string and string.
	 */
	public float compare(BufferedImage a, String b) {
		BufferedImage imgA = a;
		BufferedImage imgB = render(b);

		float[][] d = new float[imgA.getWidth() + 1][imgB.getWidth() + 1];

		for (int i = 1; i <= imgA.getWidth(); i++) {
			d[i][0] = i;
		}

		for (int j = 1; j <= imgB.getWidth(); j++) {
			d[0][j] = j;
		}

		for (int j = 0; j < imgB.getWidth(); j++) {
			for (int i = 0; i < imgA.getWidth(); i++) {
				BufferedImage sliceA = slice(imgA, i);
				BufferedImage sliceB = slice(imgB, j);

				if (same(sliceA, sliceB)) {
					d[i + 1][j + 1] = d[i][j];
				} else {
					d[i + 1][j + 1] = minimum(
						d[i][j + 1] + THRESHOLD, 
						d[i + 1][j] + THRESHOLD, 
						d[i][j] + similarity(sliceA, sliceB)
					);
				}
			}
		}

		if (DEBUG) debug(imgA, imgB, d, b);

		return d[imgA.getWidth()][imgB.getWidth()];
	}

	/**
	 * Returns the minimum of the three parameters.
	 * @param a
	 * @param b
	 * @param c
	 * @return The minimum of the three parameters.
	 */
	private static float minimum(float a, float b, float c) {
		return Math.min(a, Math.min(b, c));
	}

	/**
	 * Returns whether or not two images are identical.
	 * @param a
	 * @param b
	 * @return
	 */
	private static boolean same(BufferedImage a, BufferedImage b) {
		if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return false;

		for (int x = 0; x < a.getWidth(); x++) {
			for (int y = 0; y < a.getHeight(); y++) {
				if (a.getRGB(x, y) != b.getRGB(x, y)) return false;
			}
		}

		return true;
	}

	/**
	 * Returns the similarity between two images.
	 * @param a
	 * @param b
	 * @return A float between 0 (for identical) and 1 (for completely different).
	 */
	private static float similarity(BufferedImage a, BufferedImage b) {
		if (a == null || b == null) return 1;
		if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return 0;

		float diff = 0f;
		for (int x = 0; x < a.getWidth(); x++) {
			for (int y = 0; y < a.getHeight(); y++) {
				int rgbA = a.getRGB(x, y);
				int rgbB = b.getRGB(x, y);

				int rA = (rgbA >> 16) & 0xFF;
				int rB = (rgbB >> 16) & 0xFF;
				int gA = (rgbA >>  8) & 0xFF;
				int gB = (rgbB >>  8) & 0xFF;
				int bA = (rgbA >>  0) & 0xFF;
				int bB = (rgbB >>  0) & 0xFF;

				diff += Math.abs(rA - rB) + Math.abs(gA - gB) + Math.abs(bA - bB);
			}
		}

		diff /= a.getWidth() * a.getHeight() * 3 * 256;

		return diff;
	}

	/**
	 * Returns a one pixel wide slice of the given image with x coordinate defined my slice.
	 * @param img The image to take a slice of.
	 * @param slice The x coordinate of the slice.
	 * @return One pixel wide slice of the given image with x coordinate defined my slice.
	 */
	private static BufferedImage slice(BufferedImage img, int slice) {
		if (slice >= img.getWidth()) return null;
		return img.getSubimage(slice, 0, 1, img.getHeight());
	}

	/**
	 * Saves a visual representation of the similarity for debugging purposes.
	 */
	private static void debug(BufferedImage imgA, BufferedImage imgB, float[][] d, String b) {
		BufferedImage debug = new BufferedImage(Math.max(imgA.getWidth(), imgB.getWidth()) + (int) (d[imgA.getWidth()][imgB.getWidth()] / THRESHOLD), imgA.getHeight() + imgB.getHeight() + 8, BufferedImage.TYPE_INT_ARGB);
		int strip = 0;
		Graphics2D dg = (Graphics2D) debug.getGraphics();
		dg.setColor(new Color(127, 127, 127));
		dg.fillRect(0, 0, debug.getWidth(), debug.getHeight());
		int xA = 0, xB = 0;
		while (!(xA == imgA.getWidth() && xB == imgB.getWidth())) {
			BufferedImage sliceA = slice(imgA, xA);
			BufferedImage sliceB = slice(imgB, xB);

			if (xA + 1 >= d.length) {
				dg.setColor(new Color(0, 255, 0));
				dg.drawImage(sliceB, strip, imgA.getHeight(), null);
				dg.fillRect(strip, imgA.getHeight() + imgB.getHeight(), 1, 8);
				xB++;
			} else if (xB + 1 >= d[0].length) {
				dg.setColor(new Color(255, 0, 0));
				dg.drawImage(sliceA, strip, 0, null);
				dg.fillRect(strip, imgA.getHeight() + imgB.getHeight(), 1, 8);
				xA++;
			} else {
				float mod = d[xA + 1][xB + 1];
				float da = d[xA + 1][xB];
				float db = d[xA][xB + 1];

				float min = minimum(db, da, mod);

				if (mod == min) {
					dg.drawImage(sliceA, strip, 0, null);
					dg.drawImage(sliceB, strip, imgA.getHeight(), null);
					if (d[xA][xB] != d[xA + 1][xB + 1]) {
						float dif = d[xA + 1][xB + 1] - d[xA][xB];
						dg.setColor(new Color(0, 0, (int) (255 * Math.cos(dif*Math.PI/2))));
						dg.fillRect(strip, imgA.getHeight() + imgB.getHeight(), 1, 8);
					}
					xA++;
					xB++;
				} else if (da == min) {
					dg.setColor(new Color(255, 0, 0));
					dg.drawImage(sliceA, strip, 0, null);
					dg.fillRect(strip, imgA.getHeight() + imgB.getHeight(), 1, 8);
					xA++;
				} else if (db == min) {
					dg.setColor(new Color(0, 255, 0));
					dg.drawImage(sliceB, strip, imgA.getHeight(), null);
					dg.fillRect(strip, imgA.getHeight() + imgB.getHeight(), 1, 8);
					xB++;
				}
			}
			strip++;
		}
		try {
			ImageIO.write(debug, "PNG", new File("debug/" + b + ".png"));
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	public static void main(String[] args) throws Exception {
		StringComparer comp = new StringComparer(new Font("Arial", Font.PLAIN, 14), RenderingHints.VALUE_TEXT_ANTIALIAS_ON, new Color(255, 255, 255), new Color(0, 0, 0));

		BufferedImage reference = comp.render("example");
		System.out.println(comp.compare(reference, "test"));
	}
}

