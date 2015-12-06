package edu.berkeley.cs.nlp.ocular.sub;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import edu.berkeley.cs.nlp.ocular.sub.GlyphChar.GlyphType;
import indexer.Indexer;

/**
 * @author Dan Garrette (dhg@cs.utexas.edu)
 */
public interface GlyphSubstitutionModel extends Serializable {

	public Indexer<String> getLanguageIndexer();
	public Indexer<String> getCharacterIndexer();
	public double glyphProb(int language, GlyphType prevGlyphType, int prevLmChar, int lmChar, GlyphChar glyphChar);

	public static GlyphSubstitutionModel readGSM(String gsmPath) {
		GlyphSubstitutionModel gsm = null;
		try {
			File file = new File(gsmPath);
			if (!file.exists()) {
				throw new RuntimeException("Serialized GlyphSubstitutionModel file " + gsmPath + " not found");
			}
			FileInputStream fileIn = new FileInputStream(file);
			ObjectInputStream in = new ObjectInputStream(new GZIPInputStream(fileIn));
			gsm = (GlyphSubstitutionModel) in.readObject();
			in.close();
			fileIn.close();
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
		return gsm;
	}

	public static void writeGSM(GlyphSubstitutionModel gsm, String gsmPath) {
		try {
			new File(gsmPath).getParentFile().mkdirs();
			FileOutputStream fileOut = new FileOutputStream(gsmPath);
			ObjectOutputStream out = new ObjectOutputStream(new GZIPOutputStream(fileOut));
			out.writeObject(gsm);
			out.close();
			fileOut.close();
		} catch(IOException e) {
			throw new RuntimeException(e);
		}
	}

}
