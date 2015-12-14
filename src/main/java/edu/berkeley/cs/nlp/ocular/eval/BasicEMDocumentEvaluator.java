package edu.berkeley.cs.nlp.ocular.eval;

import static edu.berkeley.cs.nlp.ocular.data.textreader.Charset.HYPHEN;
import static edu.berkeley.cs.nlp.ocular.util.Tuple2.makeTuple2;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import edu.berkeley.cs.nlp.ocular.data.FileUtil;
import edu.berkeley.cs.nlp.ocular.data.ImageLoader.Document;
import edu.berkeley.cs.nlp.ocular.data.textreader.Charset;
import edu.berkeley.cs.nlp.ocular.eval.Evaluator.EvalSuffStats;
import edu.berkeley.cs.nlp.ocular.model.SparseTransitionModel.TransitionState;
import edu.berkeley.cs.nlp.ocular.sub.GlyphChar;
import edu.berkeley.cs.nlp.ocular.util.FileHelper;
import edu.berkeley.cs.nlp.ocular.util.StringHelper;
import edu.berkeley.cs.nlp.ocular.util.Tuple2;
import fileio.f;
import indexer.Indexer;

/**
 * @author Taylor Berg-Kirkpatrick (tberg@eecs.berkeley.edu)
 * @author Dan Garrette (dhg@cs.utexas.edu)
 */
public class BasicEMDocumentEvaluator implements EMDocumentEvaluator {
	private Indexer<String> charIndexer;
	private Indexer<String> langIndexer;
	boolean allowGlyphSubstitution;
	
	public BasicEMDocumentEvaluator(Indexer<String> charIndexer, Indexer<String> langIndexer,
			boolean allowGlyphSubstitution) {
		this.charIndexer = charIndexer;
		this.langIndexer = langIndexer;
		this.allowGlyphSubstitution = allowGlyphSubstitution;
	}

	public void printTranscriptionWithEvaluation(int iter, int batchId,
			Document doc,
			TransitionState[][] decodeStates, int[][] decodeWidths,
			boolean learnFont, String inputPath, int numEMIters,
			String outputPath,
			List<Tuple2<String, Map<String, EvalSuffStats>>> allEvals,
			List<Tuple2<String, Map<String, EvalSuffStats>>> allLmEvals) {
		String[][] text = doc.loadLineText();
		String[][] lmText = doc.loadLmLineText();
		
		//
		// Make sure the decoded states and the text have the same number of lines (numLines)
		//
		int numLines = decodeStates.length;
		if (text != null && text.length > numLines) numLines = text.length; // in case gold and viterbi have different line counts
		if (lmText != null && lmText.length > numLines) numLines = lmText.length; // in case gold and viterbi have different line counts
		
		if (text != null && text.length < numLines) {
			String[][] newText = new String[numLines][];
			for (int line = 0; line < numLines; ++line) {
				if (line < text.length)
					newText[line] = text[line];
				else
					newText[line] = new String[0];
			}
			text = newText;
		}
		if (lmText != null && lmText.length < numLines) {
			String[][] newLmText = new String[numLines][];
			for (int line = 0; line < numLines; ++line) {
				if (line < lmText.length)
					newLmText[line] = lmText[line];
				else
					newLmText[line] = new String[0];
			}
			lmText = newLmText;
		}
		if (decodeStates.length < numLines) {
			TransitionState[][] newDecodeStates = new TransitionState[numLines][];
			for (int line = 0; line < numLines; ++line) {
				if (line < decodeStates.length)
					newDecodeStates[line] = decodeStates[line];
				else
					newDecodeStates[line] = new TransitionState[0];
			}
			decodeStates = newDecodeStates;
		}

		//
		// Get the model output
		//
		@SuppressWarnings("unchecked")
		List<String>[] viterbiChars = new List[numLines];
		@SuppressWarnings("unchecked")
		List<String>[] viterbiLmChars = new List[numLines];
		@SuppressWarnings("unchecked")
		List<TransitionState>[] viterbiTransStates = new List[numLines];
		@SuppressWarnings("unchecked")
		List<Integer>[] viterbiWidths = new List[numLines];
		for (int line = 0; line < numLines; ++line) {
			viterbiChars[line] = new ArrayList<String>();
			viterbiLmChars[line] = new ArrayList<String>();
			viterbiTransStates[line] = new ArrayList<TransitionState>();
			viterbiWidths[line] = new ArrayList<Integer>();
			for (int i = 0; i < decodeStates[line].length; ++i) {
				TransitionState ts = decodeStates[line][i];
				int c = ts.getGlyphChar().templateCharIndex;
				if (viterbiChars[line].isEmpty() || !(HYPHEN.equals(viterbiChars[line].get(viterbiChars[line].size() - 1)) && HYPHEN.equals(charIndexer.getObject(c)))) {
					if (!ts.getGlyphChar().isElided) {
						viterbiChars[line].add(charIndexer.getObject(c));
					}
					viterbiLmChars[line].add(charIndexer.getObject(ts.getLmCharIndex()));
					viterbiTransStates[line].add(ts);
					viterbiWidths[line].add(decodeWidths[line][i]);
				}
			}
		}

		String fileParent = FileUtil.removeCommonPathPrefixOfParents(new File(inputPath), new File(doc.baseName()))._2;
		String preext = FileUtil.withoutExtension(new File(doc.baseName()).getName());
		String outputFilenameBase = outputPath + "/" + fileParent + "/" + preext;
		if (learnFont && numEMIters > 1) outputFilenameBase += "_iter-" + iter;
		if (batchId > 0) outputFilenameBase += "_batch-" + batchId;
		
		String transcriptionOutputFilename = outputFilenameBase + "_transcription.txt";
		String transcriptionWithSubsOutputFilename = outputFilenameBase + "_transcription_withSubs.txt";
		String transcriptionWithWidthsOutputFilename = outputFilenameBase + "_transcription_withWidths.txt";
		String goldComparisonOutputFilename = outputFilenameBase + "_vsGold.txt";
		String goldComparisonWithSubsOutputFilename = outputFilenameBase + "_vsGold_withSubs.txt";
		String htmlOutputFilename = outputFilenameBase + ".html";
		new File(transcriptionOutputFilename).getParentFile().mkdirs();
		
		//
		// Plain transcription output
		//
		{
		System.out.println("Writing transcription output to " + transcriptionOutputFilename);
		StringBuffer transcriptionOutputBuffer = new StringBuffer();
		for (int line = 0; line < numLines; ++line) {
			transcriptionOutputBuffer.append(StringHelper.join(viterbiChars[line], "") + "\n");
		}
		System.out.println(transcriptionOutputBuffer.toString() + "\n\n");
		FileHelper.writeString(transcriptionOutputFilename, transcriptionOutputBuffer.toString());
		}

		//
		// Transcription output with substitutions
		//
		List<String> transcriptionWithSubsOutputLines = new ArrayList<String>();
		if (allowGlyphSubstitution) {
		System.out.println("Transcription with substitutions");
		for (int line = 0; line < numLines; ++line) {
			StringBuilder lineBuffer = new StringBuilder();
			for (TransitionState ts : viterbiTransStates[line]) {
				int lmChar = ts.getLmCharIndex();
				GlyphChar glyph = ts.getGlyphChar();
				int glyphChar = glyph.templateCharIndex;
				String sglyphChar = Charset.unescapeChar(charIndexer.getObject(glyphChar));
				if (lmChar != glyphChar || glyph.hasElisionTilde || glyph.isElided) {
					lineBuffer.append("[" + Charset.unescapeChar(charIndexer.getObject(lmChar)) + "/" + (glyph.isElided ? "" : sglyphChar) + "]");
				}
				else {
					lineBuffer.append(sglyphChar);
				}
			}
			transcriptionWithSubsOutputLines.add(lineBuffer.toString() + "\n");
		}
		String transcriptionWithSubsOutputBuffer = StringHelper.join(transcriptionWithSubsOutputLines, "");
		//System.out.println(transcriptionWithSubsOutputBuffer.toString() + "\n\n");
		FileHelper.writeString(transcriptionWithSubsOutputFilename, transcriptionWithSubsOutputBuffer.toString());
		}

		//
		// Transcription with widths
		//
		if (allowGlyphSubstitution) {
		System.out.println("Transcription with widths");
		StringBuffer transcriptionWithWidthsOutputBuffer = new StringBuffer();
		for (int line = 0; line < numLines; ++line) {
			transcriptionWithWidthsOutputBuffer.append(transcriptionWithSubsOutputLines.get(line));
			for (int i = 0; i < viterbiTransStates[line].size(); ++i) {
				TransitionState ts = viterbiTransStates[line].get(i);
				int w = viterbiWidths[line].get(i);
				String sglyphChar = Charset.unescapeChar(charIndexer.getObject(ts.getGlyphChar().templateCharIndex));
				transcriptionWithWidthsOutputBuffer.append(sglyphChar + "[" + w + "]\n");
			}
			transcriptionWithWidthsOutputBuffer.append("\n");
		}
		//System.out.println(transcriptionWithWidthsOutputBuffer.toString());
		FileHelper.writeString(transcriptionWithWidthsOutputFilename, transcriptionWithWidthsOutputBuffer.toString());
		}

		if (text != null) {
			//
			// Evaluate against gold-transcribed data (given as "text")
			//
			@SuppressWarnings("unchecked")
			List<String>[] goldCharSequences = new List[numLines];
			@SuppressWarnings("unchecked")
			List<String>[] goldLmCharSequences = new List[numLines];
			for (int line = 0; line < numLines; ++line) {
				goldCharSequences[line] = new ArrayList<String>();
				goldLmCharSequences[line] = new ArrayList<String>();
				for (int i = 0; i < text[line].length; ++i) {
					goldCharSequences[line].add(text[line][i]);
				}
				for (int i = 0; i < lmText[line].length; ++i) {
					goldLmCharSequences[line].add(lmText[line][i]);
				}
			}

			//
			// Evaluate the comparison
			//
			Map<String, EvalSuffStats> evals = Evaluator.getUnsegmentedEval(viterbiChars, goldCharSequences);
			if (allEvals != null) {
				allEvals.add(makeTuple2(doc.baseName(), evals));
			}
			Map<String, EvalSuffStats> lmEvals = Evaluator.getUnsegmentedEval(viterbiLmChars, goldLmCharSequences);
			if (allLmEvals != null) {
				allLmEvals.add(makeTuple2(doc.baseName(), lmEvals));
			}
			
			//
			// Make comparison file
			//
			{
			StringBuffer goldComparisonOutputBuffer = new StringBuffer();
			goldComparisonOutputBuffer.append("MODEL OUTPUT vs. GOLD TRANSCRIPTION\n\n");
			for (int line = 0; line < numLines; ++line) {
				goldComparisonOutputBuffer.append(StringHelper.join(viterbiChars[line], "").trim() + "\n");
				goldComparisonOutputBuffer.append(StringHelper.join(goldCharSequences[line], "").trim() + "\n");
				goldComparisonOutputBuffer.append("\n");
			}
			goldComparisonOutputBuffer.append(Evaluator.renderEval(evals));
			System.out.println("Writing gold comparison to " + goldComparisonOutputFilename);
			//System.out.println(goldComparisonOutputBuffer.toString());
			f.writeString(goldComparisonOutputFilename, goldComparisonOutputBuffer.toString());
			}
			
			//
			// Make comparison file with substitutions
			//
			if (allowGlyphSubstitution) {
			System.out.println("Transcription with substitutions");
			StringBuffer goldComparisonWithSubsOutputBuffer = new StringBuffer();
			goldComparisonWithSubsOutputBuffer.append("MODEL OUTPUT vs. GOLD TRANSCRIPTION\n\n");
			for (int line = 0; line < numLines; ++line) {
				goldComparisonWithSubsOutputBuffer.append(transcriptionWithSubsOutputLines.get(line).trim() + "\n");
				goldComparisonWithSubsOutputBuffer.append(StringHelper.join(goldCharSequences[line], "").trim() + "\n");
				goldComparisonWithSubsOutputBuffer.append("\n");
			}
			goldComparisonWithSubsOutputBuffer.append(Evaluator.renderEval(evals));
			System.out.println("Writing gold comparison with substitutions to " + goldComparisonWithSubsOutputFilename);
			System.out.println(goldComparisonWithSubsOutputBuffer.toString() + "\n\n");
			f.writeString(goldComparisonWithSubsOutputFilename, goldComparisonWithSubsOutputBuffer.toString());
			}
		}

		if (langIndexer.size() > 1) {
			System.out.println("Multiple languages being used ("+langIndexer.size()+"), so an html file is being generated to show language switching.");
			System.out.println("Writing html output to " + htmlOutputFilename);
			f.writeString(htmlOutputFilename, printLanguageAnnotatedTranscription(numLines, viterbiTransStates, doc.baseName(), htmlOutputFilename));
		}
	}

	private String printLanguageAnnotatedTranscription(int numLines, List<TransitionState>[] viterbiTransStates, String imgFilename, String htmlOutputFilename) {
		StringBuffer outputBuffer = new StringBuffer();
		outputBuffer.append("<HTML xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\" lang=\"en\">\n");
		outputBuffer.append("<HEAD><META http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"></HEAD>\n");
		outputBuffer.append("<body>\n");
		outputBuffer.append("<table><tr><td>\n");

		String[] colors = new String[] { "Black", "Red", "Blue", "Olive", "Orange", "Magenta", "Lime", "Cyan", "Purple", "Green", "Brown" };

		int prevLanguage = -1;
		for (int line = 0; line < numLines; ++line) {
			for (TransitionState ts : viterbiTransStates[line]) {
				int lmChar = ts.getLmCharIndex();
				GlyphChar glyph = ts.getGlyphChar();
				int glyphChar = glyph.templateCharIndex;
				String sglyphChar = Charset.unescapeChar(charIndexer.getObject(glyphChar));

				int currLanguage = ts.getLanguageIndex();
				if (currLanguage != prevLanguage) {
					outputBuffer.append("<font color=\"" + colors[currLanguage+1] + "\">");
				}
				
				if (lmChar != glyphChar || glyph.hasElisionTilde || glyph.isElided)
					outputBuffer.append("[" + Charset.unescapeChar(charIndexer.getObject(lmChar)) + "/" + (glyph.isElided ? "" : sglyphChar) + "]");
				else
					outputBuffer.append(sglyphChar);
				
				prevLanguage = currLanguage;
			}
			outputBuffer.append("</br>\n");
		}
		outputBuffer.append("</font></font><br/><br/><br/>\n");
		for (int i = -1; i < langIndexer.size(); ++i) {
			outputBuffer.append("<font color=\"" + colors[i+1] + "\">" + (i < 0 ? "none" : langIndexer.getObject(i)) + "</font></br>\n");
		}

		outputBuffer.append("</td><td><img src=\"" + FileUtil.pathRelativeTo(imgFilename, new File(htmlOutputFilename).getParent()) + "\">\n");
		outputBuffer.append("</td></tr></table>\n");
		outputBuffer.append("</body></html>\n");
		outputBuffer.append("\n\n\n");
		outputBuffer.append("\n\n\n\n\n");
		return outputBuffer.toString();
	}

}
